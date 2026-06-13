#!/usr/bin/env python3
"""AI-X Cursor Hook ingest script — fail-open by design."""

from __future__ import annotations

import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any


def log(message: str) -> None:
    print(message, file=sys.stderr)


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def read_payload() -> dict[str, Any]:
    raw = sys.stdin.read()
    if not raw.strip():
        return {}
    return json.loads(raw)


def resolve_event(payload: dict[str, Any], argv: list[str]) -> str:
    if len(argv) > 1 and argv[1]:
        return argv[1]
    for key in ("hook_event_name", "event", "hookEvent"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    if payload.get("prompt") is not None:
        return "beforeSubmitPrompt"
    if payload.get("response") is not None:
        return "afterAgentResponse"
    if payload.get("status") is not None and payload.get("conversation_id"):
        return "stop"
    if payload.get("conversation_id") is not None:
        return "sessionStart"
    return "unknown"


def session_id(payload: dict[str, Any]) -> str:
    for key in ("conversation_id", "sessionId", "session_id"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    return ""


def client_message_id(payload: dict[str, Any], role: str, content: str, sid: str) -> str:
    for key in ("message_id", "messageId", "clientMessageId"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    digest = hashlib.sha256(f"{sid}|{role}|{content}".encode("utf-8")).hexdigest()[:32]
    return digest


def metadata(payload: dict[str, Any], event: str) -> dict[str, Any]:
    meta = {"hookEvent": event}
    for key in ("model", "workspace_roots", "cursor_version"):
        if key in payload:
            meta[key] = payload[key]
    return meta


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


def main() -> int:
    api_base = env("AIX_API_BASE", "http://127.0.0.1:8080").rstrip("/")
    token = env("AIX_INGEST_TOKEN")
    timeout = float(env("AIX_HTTP_TIMEOUT", "3"))
    auto_end = env("AIX_AUTO_END_SESSION", "true").lower() != "false"

    payload = read_payload()
    event = resolve_event(payload, sys.argv)
    sid = session_id(payload)

    if not sid:
        log(f"skip event={event}: missing conversation_id")
        return 0

    started = None

    try:
        if event == "sessionStart":
            started = post_json(
                f"{api_base}/api/ingest/sessions",
                token,
                {
                    "sessionId": sid,
                    "source": "cursor",
                    "metadata": metadata(payload, event),
                },
                timeout,
            )
        elif event == "beforeSubmitPrompt":
            content = payload.get("prompt") or payload.get("content") or ""
            started = post_json(
                f"{api_base}/api/ingest/messages",
                token,
                {
                    "sessionId": sid,
                    "role": "user",
                    "content": content,
                    "clientMessageId": client_message_id(payload, "user", str(content), sid),
                    "metadata": metadata(payload, event),
                },
                timeout,
            )
        elif event == "afterAgentResponse":
            content = payload.get("response") or payload.get("content") or ""
            started = post_json(
                f"{api_base}/api/ingest/messages",
                token,
                {
                    "sessionId": sid,
                    "role": "assistant",
                    "content": content,
                    "clientMessageId": client_message_id(payload, "assistant", str(content), sid),
                    "metadata": metadata(payload, event),
                },
                timeout,
            )
        elif event == "stop":
            if auto_end:
                started = post_json(
                    f"{api_base}/api/ingest/sessions/{sid}/end",
                    token,
                    {},
                    timeout,
                )
        else:
            log(f"skip unsupported event={event}")
            return 0
    except Exception as exc:  # noqa: BLE001 — fail-open
        log(f"fail-open event={event} sessionId={sid} error={exc}")
        return 0

    if started is not None:
        status, body = started
        log(f"event={event} sessionId={sid} status={status} body={body[:200]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
