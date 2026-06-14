#!/usr/bin/env python3
"""AI-X Cursor Hook ingest script — fail-open by design."""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import time
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

# 与 com.aix.common.model.MessageType 的 code 保持一致
MESSAGE_TYPE_USER = 1
MESSAGE_TYPE_ASSISTANT = 2
MESSAGE_TYPE_THOUGHT = 3
MESSAGE_TYPE_SYSTEM = 4

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
    """Return (text, encoding_used). Cursor Hook JSON is always UTF-8."""
    if not raw_bytes.strip():
        return "", "empty"

    if raw_bytes.startswith(b"\xff\xfe"):
        return raw_bytes.decode("utf-16-le"), "utf-16-le-bom"
    if raw_bytes.startswith(b"\xfe\xff"):
        return raw_bytes.decode("utf-16-be"), "utf-16-be-bom"
    if raw_bytes.startswith(b"\xef\xbb\xbf"):
        return raw_bytes.decode("utf-8-sig"), "utf-8-sig"

    # Hook payload is UTF-8 JSON. Do not try GBK — on CP936 Windows it can
    # produce parseable JSON with mojibake Chinese (double-encoding).
    try:
        text = raw_bytes.decode("utf-8")
        json.loads(text)
        return text.strip().lstrip("\ufeff"), "utf-8"
    except UnicodeDecodeError:
        pass
    except json.JSONDecodeError:
        text = raw_bytes.decode("utf-8", errors="replace").strip().lstrip("\ufeff")
        if text:
            return text, "utf-8-replace"

    if looks_like_utf16_le(raw_bytes):
        for encoding, label in (("utf-16-le", "utf-16-le"), ("utf-16-be", "utf-16-be")):
            try:
                text = raw_bytes.decode(encoding)
                json.loads(text)
                return text.strip().lstrip("\ufeff"), label
            except (UnicodeDecodeError, json.JSONDecodeError):
                continue

    return raw_bytes.decode("utf-8", errors="replace").strip().lstrip("\ufeff"), "utf-8-replace"


def find_windows_hook_payload_file(max_age_seconds: float = 10.0) -> Path | None:
    """
    Cursor on Windows writes hook JSON to %TEMP%\\cursor-hook-payload-*.json (UTF-8),
    then pipes via PowerShell Get-Content without -Encoding UTF8 — stdin is often
    mojibake on CP936. Read the temp file directly (same source as Hooks UI).
    """
    if sys.platform != "win32":
        return None
    temp_root = env("TEMP") or env("TMP")
    if not temp_root:
        return None
    temp_dir = Path(temp_root)
    if not temp_dir.is_dir():
        return None

    now = time.time()
    newest: Path | None = None
    newest_mtime = 0.0
    for path in temp_dir.glob("cursor-hook-payload-*.json"):
        try:
            mtime = path.stat().st_mtime
        except OSError:
            continue
        if now - mtime > max_age_seconds:
            continue
        if mtime >= newest_mtime:
            newest_mtime = mtime
            newest = path
    return newest


def collect_input_bytes() -> tuple[bytes, str]:
    for arg in sys.argv[1:]:
        path = Path(arg)
        if path.is_file():
            return path.read_bytes(), "argv"

    for env_key in ("CURSOR_HOOK_INPUT", "CURSOR_HOOK_PAYLOAD_PATH"):
        env_path = env(env_key)
        if env_path:
            path = Path(env_path)
            if path.is_file():
                return path.read_bytes(), "env"

    if sys.platform == "win32":
        payload_path = find_windows_hook_payload_file()
        if payload_path is not None:
            try:
                return payload_path.read_bytes(), "windows_temp_file"
            except OSError:
                pass

    return sys.stdin.buffer.read(), "stdin"


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
    recovered=True when stdin JSON was damaged but metadata/body was salvaged.
    """
    try:
        payload = json.loads(raw)
        if _payload_body_empty(payload):
            salvaged = _salvage_body_fields(raw)
            if salvaged:
                payload.update(salvaged)
                return payload, True
        return payload, False
    except json.JSONDecodeError:
        pass

    salvaged = _salvage_body_fields(raw)
    if salvaged:
        payload = scrape_payload_fields(raw)
        payload.update(salvaged)
        return payload, True

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


def _payload_body_empty(payload: dict[str, Any]) -> bool:
    for key in ("prompt", "text", "response", "content"):
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return False
    return True


def _salvage_body_fields(raw: str) -> dict[str, str]:
    salvaged: dict[str, str] = {}
    for field, next_field in (
        ("prompt", "attachments"),
        ("prompt", "input_tokens"),
        ("text", "input_tokens"),
        ("response", "input_tokens"),
        ("content", "input_tokens"),
    ):
        if field in salvaged:
            continue
        text = extract_body_field(raw, field, next_field)
        if text.strip():
            salvaged[field] = text
    return salvaged


def pick_user_content(payload: dict[str, Any], raw: str, _recovered: bool) -> tuple[str, str]:
    """Prefer parsed JSON prompt; fall back to raw extraction when body was damaged."""
    text = user_content(payload)
    if text.strip():
        return text, "json"

    for next_field in ("input_tokens", "attachments"):
        text = extract_body_field(raw, "prompt", next_field)
        if text.strip():
            return text, "extract"
    return "", "none"


def pick_assistant_content(payload: dict[str, Any], raw: str, _recovered: bool) -> tuple[str, str]:
    text = assistant_content(payload)
    if text.strip():
        return text, "json"

    for field in ("text", "response", "content"):
        text = extract_body_field(raw, field, "input_tokens")
        if text.strip():
            return text, "extract"
    return "", "none"


def pick_thought_content(payload: dict[str, Any], raw: str, recovered: bool) -> tuple[str, str]:
    """afterAgentThought uses the same `text` field as assistant output."""
    return pick_assistant_content(payload, raw, recovered)


def resolve_message_bodies(
    payload: dict[str, Any],
    raw: str = "",
    recovered: bool = False,
) -> dict[str, Any]:
    event = resolve_event(payload, sys.argv)

    if event == "beforeSubmitPrompt":
        content, source = pick_user_content(payload, raw, recovered)
        if content:
            payload["prompt"] = content
            payload["_content_source"] = source
    elif event == "afterAgentResponse":
        content, source = pick_assistant_content(payload, raw, recovered)
        if content:
            payload["text"] = content
            payload["_content_source"] = source
    elif event == "afterAgentThought":
        content, source = pick_thought_content(payload, raw, recovered)
        if content:
            payload["text"] = content
            payload["_content_source"] = source

    return payload


def read_payload() -> tuple[dict[str, Any], dict[str, Any], str, bool, str, str]:
    raw_bytes, input_source = collect_input_bytes()
    raw, input_encoding = decode_raw_bytes(raw_bytes)
    if not raw:
        return {}, {}, "", False, input_encoding, input_source

    hook_input, recovered = parse_payload(raw)
    if recovered:
        write_file_log(
            "info",
            "hook JSON recovered from damaged stdin",
            inputEncoding=input_encoding,
            inputSource=input_source,
            hookEvent=resolve_event(hook_input, sys.argv),
        )

    payload = resolve_message_bodies(dict(hook_input), raw, recovered)
    return payload, hook_input, raw, recovered, input_encoding, input_source


def resolve_event(payload: dict[str, Any], argv: list[str]) -> str:
    if len(argv) > 1 and argv[1] and not Path(argv[1]).is_file():
        return argv[1]
    for key in ("hook_event_name", "event", "hookEvent"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    if payload.get("prompt") is not None:
        return "beforeSubmitPrompt"
    if payload.get("response") is not None or (
        payload.get("text") is not None
        and payload.get("hook_event_name") != "afterAgentThought"
    ):
        return "afterAgentResponse"
    if payload.get("text") is not None:
        return "afterAgentThought"
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


def thought_content(payload: dict[str, Any]) -> str:
    value = payload.get("text")
    return value if isinstance(value, str) else ""


def assistant_content(payload: dict[str, Any]) -> str:
    for key in ("text", "response", "content"):
        value = payload.get(key)
        if isinstance(value, str):
            return value
    return ""


def resolve_message_id(payload: dict[str, Any], content: str, sid: str, event: str = "") -> str:
    """Cursor generation_id; same turn shares id, distinguished by event code."""
    if event == "afterAgentThought" and content.strip():
        gen = ""
        for key in ("generation_id", "message_id", "messageId", "clientMessageId"):
            value = payload.get(key)
            if isinstance(value, str) and value:
                gen = value
                break
        digest = hashlib.sha256(f"{gen}|thought|{content}".encode("utf-8")).hexdigest()[:32]
        return digest
    for key in ("generation_id", "message_id", "messageId", "clientMessageId"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    digest = hashlib.sha256(f"{sid}|{content}".encode("utf-8")).hexdigest()[:32]
    return digest


def hook_metadata(hook_input: dict[str, Any], payload: dict[str, Any] | None = None) -> dict[str, Any]:
    """保留 Hook 元数据；正文只用 resolve 后的 prompt/text，避免 stdin 乱码写入 metadata。"""
    keep_keys = (
        "conversation_id",
        "session_id",
        "generation_id",
        "hook_event_name",
        "model",
        "composer_mode",
        "cursor_version",
        "workspace_roots",
        "user_email",
        "attachments",
        "duration_ms",
    )
    meta: dict[str, Any] = {}
    if hook_input:
        for key in keep_keys:
            if key in hook_input:
                meta[key] = hook_input[key]
    if payload:
        for key in ("prompt", "text"):
            value = payload.get(key)
            if isinstance(value, str) and value:
                meta[key] = value
    return meta


def ingest_message_body(
    payload: dict[str, Any],
    hook_input: dict[str, Any],
    event: str,
    sid: str,
    message_type: int,
    content: str,
) -> dict[str, Any]:
    return {
        "sessionId": sid,
        "messageType": message_type,
        "content": content,
        "messageId": resolve_message_id(payload, content, sid, event),
        "event": hook_event_code(event),
        "metadata": hook_metadata(hook_input, payload),
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
        messageType=body.get("messageType") if body else None,
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

        payload, hook_input, raw, recovered, input_encoding, input_source = read_payload()
        event = resolve_event(payload, sys.argv)
        sid = session_id(payload)

        write_file_log(
            "info",
            "hook start",
            event=event,
            sessionId=sid or None,
            inputSource=input_source,
            inputEncoding=input_encoding,
            jsonRecovered=recovered,
            generationId=payload.get("generation_id"),
            contentSource=payload.get("_content_source"),
            promptPreview=(payload.get("prompt") or "")[:80] or None,
            textPreview=(payload.get("text") or "")[:80] or None,
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
                    "metadata": hook_metadata(hook_input, payload),
                }
                started = post_json(f"{api_base}/api/ingest/sessions", token, request_body, timeout)
            elif event == "beforeSubmitPrompt":
                content = user_content(payload)
                if not content.strip():
                    write_file_log(
                        "warn",
                        "skip empty user content",
                        event=event,
                        sessionId=sid,
                        jsonRecovered=recovered,
                        inputEncoding=input_encoding,
                        generationId=payload.get("generation_id"),
                        rawPreview=raw[:300] if raw else None,
                    )
                    return 0
                request_body = ingest_message_body(
                    payload, hook_input, event, sid, MESSAGE_TYPE_USER, content
                )
                log_ingest(event, sid, payload, request_body, inputEncoding=input_encoding, jsonRecovered=recovered)
                started = post_json(f"{api_base}/api/ingest/messages", token, request_body, timeout)
            elif event == "afterAgentResponse":
                content = assistant_content(payload)
                if not content.strip():
                    write_file_log(
                        "warn",
                        "skip empty assistant content",
                        event=event,
                        sessionId=sid,
                        jsonRecovered=recovered,
                        inputEncoding=input_encoding,
                        generationId=payload.get("generation_id"),
                        rawPreview=raw[:300] if raw else None,
                    )
                    return 0
                request_body = ingest_message_body(
                    payload, hook_input, event, sid, MESSAGE_TYPE_ASSISTANT, content
                )
                log_ingest(event, sid, payload, request_body, inputEncoding=input_encoding, jsonRecovered=recovered)
                started = post_json(f"{api_base}/api/ingest/messages", token, request_body, timeout)
            elif event == "afterAgentThought":
                content = thought_content(payload)
                if not content.strip():
                    write_file_log(
                        "warn",
                        "skip empty thought content",
                        event=event,
                        sessionId=sid,
                        jsonRecovered=recovered,
                        inputEncoding=input_encoding,
                        generationId=payload.get("generation_id"),
                        rawPreview=raw[:300] if raw else None,
                    )
                    return 0
                request_body = ingest_message_body(
                    payload, hook_input, event, sid, MESSAGE_TYPE_THOUGHT, content
                )
                log_ingest(event, sid, payload, request_body, inputEncoding=input_encoding, jsonRecovered=recovered)
                started = post_json(f"{api_base}/api/ingest/messages", token, request_body, timeout)
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
                log_error(
                    f"event={event} sessionId={sid} status={status} body={body[:300]} "
                    f"contentLen={len(request_body.get('content', '')) if request_body else 0} "
                    f"messageId={request_body.get('messageId') if request_body else None}"
                )
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
