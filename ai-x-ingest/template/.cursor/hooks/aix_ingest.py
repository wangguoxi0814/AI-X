#!/usr/bin/env python3
"""AI-X Cursor Hook ingest script — fail-open by design."""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import traceback
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any


# 与 com.aix.common.model.CursorHookEvent 的 code 保持一致
HOOK_EVENT_CODES: dict[str, int] = {
    "sessionStart": 1,
    "sessionEnd": 2,
    "preToolUse": 3,
    "postToolUse": 4,
    "postToolUseFailure": 5,
    "subagentStart": 6,
    "subagentStop": 7,
    "beforeShellExecution": 8,
    "afterShellExecution": 9,
    "beforeMCPExecution": 10,
    "afterMCPExecution": 11,
    "beforeReadFile": 12,
    "afterFileEdit": 13,
    "beforeSubmitPrompt": 14,
    "preCompact": 15,
    "stop": 16,
    "afterAgentResponse": 17,
    "afterAgentThought": 18,
    "beforeTabFileRead": 19,
    "afterTabFileEdit": 20,
    "workspaceOpen": 21,
}

SCRIPT_DIR = Path(__file__).resolve().parent
LOG_DIR = SCRIPT_DIR / "logs"


def hook_event_code(event: str) -> int:
    return HOOK_EVENT_CODES.get(event, 0)


def configure_stdio_encoding() -> None:
    """Ensure UTF-8 on Windows Hook channels."""
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            try:
                reconfigure(encoding="utf-8", errors="replace")
            except (OSError, ValueError):
                pass


def emit_hook_ok() -> None:
    """Cursor parses stdout as JSON; ingest hooks must not block the agent."""
    print("{}", file=sys.stdout, flush=True)


def hook_debug() -> bool:
    return env("AIX_HOOK_DEBUG", "").lower() in ("1", "true", "yes")


def log(message: str) -> None:
    """Diagnostic only — Cursor treats all stderr as Error Output."""
    if hook_debug():
        print(message, file=sys.stderr, flush=True)


def log_error(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def write_file_log(level: str, message: str, **fields: Any) -> None:
    """Append JSON lines to .cursor/hooks/logs/aix-ingest-YYYY-MM-DD.log"""
    try:
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        record = {
            "ts": datetime.now().isoformat(timespec="seconds"),
            "level": level,
            "message": message,
            **fields,
        }
        log_path = LOG_DIR / f"aix-ingest-{datetime.now():%Y-%m-%d}.log"
        with log_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    except OSError:
        pass


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def looks_like_utf16_le(raw_bytes: bytes) -> bool:
    if len(raw_bytes) < 4 or len(raw_bytes) % 2 != 0:
        return False
    sample = raw_bytes[: min(len(raw_bytes), 400)]
    even_nulls = sum(1 for index in range(0, len(sample), 2) if sample[index] == 0)
    return even_nulls >= max(8, len(sample) // 4)


def decode_raw_bytes(raw_bytes: bytes) -> tuple[str, str]:
    """Return (text, encoding_used). Prefer encodings that yield valid hook JSON."""
    if not raw_bytes.strip():
        return "", "empty"

    if raw_bytes.startswith(b"\xff\xfe"):
        return raw_bytes.decode("utf-16-le"), "utf-16-le-bom"
    if raw_bytes.startswith(b"\xfe\xff"):
        return raw_bytes.decode("utf-16-be"), "utf-16-be-bom"
    if raw_bytes.startswith(b"\xef\xbb\xbf"):
        return raw_bytes.decode("utf-8-sig"), "utf-8-sig"

    candidates: list[tuple[str, str]] = []
    if looks_like_utf16_le(raw_bytes):
        candidates.append(("utf-16-le", "utf-16-le"))
    candidates.extend(
        [
            ("utf-8", "utf-8"),
            ("utf-16-le", "utf-16-le"),
            ("utf-16-be", "utf-16-be"),
            ("gbk", "gbk"),
        ]
    )

    seen: set[str] = set()
    best_text = ""
    best_encoding = "utf-8-replace"
    for encoding, label in candidates:
        if encoding in seen:
            continue
        seen.add(encoding)
        try:
            text = raw_bytes.decode(encoding)
        except UnicodeDecodeError:
            continue
        try:
            json.loads(text)
            return text.strip().lstrip("\ufeff"), label
        except json.JSONDecodeError:
            if not best_text:
                best_text = text
                best_encoding = label

    if best_text:
        return best_text.strip().lstrip("\ufeff"), best_encoding
    return raw_bytes.decode("utf-8", errors="replace").strip().lstrip("\ufeff"), "utf-8-replace"


def collect_input_bytes() -> bytes:
    for arg in sys.argv[1:]:
        path = Path(arg)
        if path.is_file():
            return path.read_bytes()

    for env_key in ("CURSOR_HOOK_INPUT", "CURSOR_HOOK_PAYLOAD_PATH"):
        env_path = env(env_key)
        if env_path:
            path = Path(env_path)
            if path.is_file():
                return path.read_bytes()

    return sys.stdin.buffer.read()


def scrape_payload_fields(raw: str) -> dict[str, Any]:
    """Recover ASCII-safe hook metadata when full JSON parsing fails on Windows."""
    payload: dict[str, Any] = {}
    simple_patterns = {
        "conversation_id": r'"conversation_id"\s*:\s*"([0-9a-f-]+)"',
        "session_id": r'"session_id"\s*:\s*"([0-9a-f-]+)"',
        "generation_id": r'"generation_id"\s*:\s*"([0-9a-f-]+)"',
        "hook_event_name": r'"hook_event_name"\s*:\s*"([^"]+)"',
        "model": r'"model"\s*:\s*"([^"]+)"',
    }
    for key, pattern in simple_patterns.items():
        match = re.search(pattern, raw)
        if match:
            payload[key] = match.group(1)

    transcript_match = re.search(
        r'"transcript_path"\s*:\s*"((?:[^"\\]|\\.)*)"',
        raw,
    )
    if transcript_match:
        try:
            payload["transcript_path"] = json.loads(f'"{transcript_match.group(1)}"')
        except json.JSONDecodeError:
            payload["transcript_path"] = transcript_match.group(1).replace("\\\\", "\\")

    return payload


def extract_body_field(raw: str, field: str, next_field: str) -> str:
    """Salvage a message body when JSON is invalid but ASCII anchors remain."""
    match = re.search(
        rf'"{field}"\s*:\s*"(.*)"\s*,\s*"{next_field}"',
        raw,
        flags=re.DOTALL,
    )
    if not match:
        return ""
    escaped = match.group(1)
    try:
        return json.loads(f'"{escaped}"')
    except json.JSONDecodeError:
        return (
            escaped.replace("\\n", "\n")
            .replace('\\"', '"')
            .replace("\\\\", "\\")
        )


def strip_corrupt_body_field(raw: str, field: str, next_field: str) -> str:
    """Drop a corrupt UTF-8 string value; following keys are ASCII-safe on Windows."""
    pattern = rf'("{field}"\s*:\s*").*?("\s*,\s*"{next_field}")'
    return re.sub(pattern, rf'\1"\2', raw, count=1, flags=re.DOTALL)


def parse_payload(raw: str) -> tuple[dict[str, Any], bool]:
    """
    Parse hook JSON. Returns (payload, recovered).
    recovered=True when stdin JSON was damaged but metadata was salvaged.
    """
    try:
        return json.loads(raw), False
    except json.JSONDecodeError:
        pass

    for field, next_field in (
        ("text", "input_tokens"),
        ("prompt", "input_tokens"),
        ("content", "input_tokens"),
        ("response", "input_tokens"),
    ):
        repaired = strip_corrupt_body_field(raw, field, next_field)
        if repaired != raw:
            try:
                return json.loads(repaired), True
            except json.JSONDecodeError:
                pass

    try:
        return scrape_payload_fields(raw), True
    except Exception:
        return {}, True


def load_transcript(path: Path) -> list[dict[str, Any]]:
    entries: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        entries.append(json.loads(line))
    return entries


def message_text(entry: dict[str, Any]) -> str:
    content = entry.get("message", {}).get("content", [])
    if not isinstance(content, list):
        return ""
    parts: list[str] = []
    for block in content:
        if isinstance(block, dict) and block.get("type") == "text":
            text = block.get("text")
            if isinstance(text, str) and text:
                parts.append(text)
    return "\n".join(parts)


def last_message_text(entries: list[dict[str, Any]], role: str) -> str:
    for entry in reversed(entries):
        if entry.get("role") == role:
            text = message_text(entry)
            if text:
                return text
    return ""


def last_user_index(entries: list[dict[str, Any]]) -> int:
    for index in range(len(entries) - 1, -1, -1):
        if entries[index].get("role") == "user":
            return index
    return -1


def last_message_text_after(entries: list[dict[str, Any]], role: str, after_index: int) -> str:
    for entry in reversed(entries[after_index + 1 :]):
        if entry.get("role") == role:
            text = message_text(entry)
            if text:
                return text
    return ""


def load_transcript_entries(payload: dict[str, Any]) -> list[dict[str, Any]]:
    transcript_path = payload.get("transcript_path")
    if not isinstance(transcript_path, str) or not transcript_path:
        return []

    path = Path(transcript_path)
    if not path.is_file():
        write_file_log("warn", "transcript not found", transcriptPath=transcript_path)
        return []

    try:
        return load_transcript(path)
    except (OSError, json.JSONDecodeError) as exc:
        write_file_log("error", "transcript read failed", error=str(exc), transcriptPath=transcript_path)
        return []


def pick_user_content(
    payload: dict[str, Any],
    raw: str,
    recovered: bool,
    entries: list[dict[str, Any]],
) -> tuple[str, str]:
    """Prefer transcript UTF-8 over hook stdin (often garbled on Windows)."""
    if entries:
        text = last_message_text(entries, "user")
        if text.strip():
            return text, "transcript"

    if not recovered:
        text = user_content(payload)
        if text.strip():
            return text, "json"

    for next_field in ("input_tokens", "attachments"):
        text = extract_body_field(raw, "prompt", next_field)
        if text.strip():
            return text, "extract"
    return "", "none"


def pick_assistant_content(
    payload: dict[str, Any],
    raw: str,
    recovered: bool,
    entries: list[dict[str, Any]],
) -> tuple[str, str]:
    if entries:
        user_idx = last_user_index(entries)
        text = last_message_text_after(entries, "assistant", user_idx)
        if text.strip():
            return text, "transcript"

    if not recovered:
        text = assistant_content(payload)
        if text.strip():
            return text, "json"

    for field in ("text", "response", "content"):
        text = extract_body_field(raw, field, "input_tokens")
        if text.strip():
            return text, "extract"
    return "", "none"


def enrich_from_transcript(
    payload: dict[str, Any],
    raw: str = "",
    recovered: bool = False,
) -> dict[str, Any]:
    """
    Windows: hook stdin JSON text fields are often corrupted.
    transcript JSONL is reliable UTF-8 — always prefer it for message bodies.
    """
    event = resolve_event(payload, sys.argv)
    entries = load_transcript_entries(payload)

    if event == "beforeSubmitPrompt":
        content, source = pick_user_content(payload, raw, recovered, entries)
        if content:
            payload["prompt"] = content
            payload["_content_source"] = source
    elif event == "afterAgentResponse":
        content, source = pick_assistant_content(payload, raw, recovered, entries)
        if content:
            payload["text"] = content
            payload["_content_source"] = source

    return payload


def read_payload() -> tuple[dict[str, Any], dict[str, Any], str, bool, str]:
    raw_bytes = collect_input_bytes()
    raw, input_encoding = decode_raw_bytes(raw_bytes)
    if not raw:
        return {}, {}, "", False, input_encoding

    hook_input, recovered = parse_payload(raw)
    if recovered:
        write_file_log(
            "info",
            "hook JSON recovered from damaged stdin",
            inputEncoding=input_encoding,
            hookEvent=resolve_event(hook_input, sys.argv),
        )

    payload = enrich_from_transcript(dict(hook_input), raw, recovered)
    return payload, hook_input, raw, recovered, input_encoding


def resolve_event(payload: dict[str, Any], argv: list[str]) -> str:
    if len(argv) > 1 and argv[1] and not Path(argv[1]).is_file():
        return argv[1]
    for key in ("hook_event_name", "event", "hookEvent"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    if payload.get("prompt") is not None:
        return "beforeSubmitPrompt"
    if payload.get("response") is not None or payload.get("text") is not None:
        return "afterAgentResponse"
    if payload.get("status") is not None and session_id(payload):
        return "stop"
    if session_id(payload):
        return "sessionStart"
    return "unknown"


def session_id(payload: dict[str, Any]) -> str:
    for key in ("conversation_id", "sessionId", "session_id"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    return ""


def user_content(payload: dict[str, Any]) -> str:
    value = payload.get("prompt") or payload.get("content")
    return value if isinstance(value, str) else ""


def assistant_content(payload: dict[str, Any]) -> str:
    for key in ("text", "response", "content"):
        value = payload.get(key)
        if isinstance(value, str):
            return value
    return ""


def resolve_message_id(payload: dict[str, Any], content: str, sid: str) -> str:
    """Cursor generation_id; same turn shares id, distinguished by event code."""
    for key in ("generation_id", "message_id", "messageId", "clientMessageId"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    digest = hashlib.sha256(f"{sid}|{content}".encode("utf-8")).hexdigest()[:32]
    return digest


def hook_metadata(hook_input: dict[str, Any]) -> dict[str, Any]:
    """原样保留 Cursor Hook 事件 Input，写入后端 metadata_json。"""
    return dict(hook_input) if hook_input else {}


def ingest_message_body(
    payload: dict[str, Any],
    hook_input: dict[str, Any],
    event: str,
    sid: str,
    role: str,
    content: str,
) -> dict[str, Any]:
    return {
        "sessionId": sid,
        "role": role,
        "content": content,
        "messageId": resolve_message_id(payload, content, sid),
        "event": hook_event_code(event),
        "metadata": hook_metadata(hook_input),
    }


def post_json(url: str, token: str, body: dict[str, Any], timeout: float) -> tuple[int, str]:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        return exc.code, body_text


def log_ingest(event: str, sid: str, payload: dict[str, Any], body: dict[str, Any] | None, **extra: Any) -> None:
    content = ""
    if body is not None:
        content = str(body.get("content", ""))
    write_file_log(
        "info",
        "ingest",
        event=event,
        sessionId=sid,
        messageId=body.get("messageId") if body else None,
        eventCode=body.get("event") if body else None,
        role=body.get("role") if body else None,
        contentSource=payload.get("_content_source"),
        contentLength=len(content),
        contentPreview=content[:200],
        **extra,
    )


def main() -> int:
    event = "unknown"
    sid = ""
    try:
        api_base = env("AIX_API_BASE", "http://127.0.0.1:8080").rstrip("/")
        token = env("AIX_INGEST_TOKEN")
        timeout = float(env("AIX_HTTP_TIMEOUT", "3"))
        auto_end = env("AIX_AUTO_END_SESSION", "true").lower() != "false"

        payload, hook_input, _raw, recovered, input_encoding = read_payload()
        event = resolve_event(payload, sys.argv)
        sid = session_id(payload)

        write_file_log(
            "info",
            "hook start",
            event=event,
            sessionId=sid or None,
            inputEncoding=input_encoding,
            jsonRecovered=recovered,
            generationId=payload.get("generation_id"),
        )

        if not sid:
            write_file_log("warn", "skip missing session id", event=event)
            return 0

        started = None
        request_body: dict[str, Any] | None = None

        try:
            if event == "sessionStart":
                request_body = {
                    "sessionId": sid,
                    "source": "cursor",
                    "metadata": hook_metadata(hook_input),
                }
                started = post_json(f"{api_base}/api/ingest/sessions", token, request_body, timeout)
            elif event == "beforeSubmitPrompt":
                content = user_content(payload)
                request_body = ingest_message_body(payload, hook_input, event, sid, "user", content)
                log_ingest(event, sid, payload, request_body, inputEncoding=input_encoding, jsonRecovered=recovered)
                started = post_json(f"{api_base}/api/ingest/messages", token, request_body, timeout)
            elif event == "afterAgentResponse":
                content = assistant_content(payload)
                request_body = ingest_message_body(payload, hook_input, event, sid, "assistant", content)
                log_ingest(event, sid, payload, request_body, inputEncoding=input_encoding, jsonRecovered=recovered)
                started = post_json(f"{api_base}/api/ingest/messages", token, request_body, timeout)
            elif event == "afterAgentThought":
                write_file_log("info", "skip afterAgentThought", sessionId=sid)
                return 0
            elif event == "stop":
                if auto_end:
                    started = post_json(
                        f"{api_base}/api/ingest/sessions/{sid}/end",
                        token,
                        {},
                        timeout,
                    )
            else:
                write_file_log("info", "skip unsupported event", event=event, sessionId=sid)
                return 0
        except Exception as exc:  # noqa: BLE001 — fail-open
            write_file_log(
                "error",
                "ingest failed",
                event=event,
                sessionId=sid,
                error=str(exc),
                traceback=traceback.format_exc(),
            )
            log_error(f"fail-open event={event} sessionId={sid} error={exc}")
            return 0

        if started is not None:
            status, body = started
            write_file_log(
                "info" if status < 400 else "error",
                "ingest response",
                event=event,
                sessionId=sid,
                httpStatus=status,
                responseBody=body[:500],
            )
            if status >= 400:
                log_error(f"event={event} sessionId={sid} status={status} body={body[:200]}")
            elif '"duplicate":true' in body.replace(" ", "").lower():
                log(f"event={event} sessionId={sid} duplicate messageId+event")
        return 0
    except Exception as exc:  # noqa: BLE001 — fail-open
        write_file_log(
            "error",
            "hook crashed",
            event=event,
            sessionId=sid or None,
            error=str(exc),
            traceback=traceback.format_exc(),
        )
        log_error(traceback.format_exc())
        return 0
    finally:
        emit_hook_ok()


if __name__ == "__main__":
    configure_stdio_encoding()
    try:
        sys.exit(main())
    except SystemExit:
        pass
    except Exception:
        log_error(traceback.format_exc())
        sys.exit(0)
