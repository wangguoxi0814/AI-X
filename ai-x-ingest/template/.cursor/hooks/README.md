# Cursor Hooks 安装说明

将本目录 `template/.cursor/` **复制到你的业务项目根目录**（不是 AI-X 服务端仓库）。

## 目录结构

```
your-project/
└── .cursor/
    ├── hooks.json
    └── hooks/
        ├── aix_ingest.py      # 主逻辑（跨平台）
        └── README.md
```

macOS / Linux 可将 `hooks.json` 中的 command 改为：

```json
"command": "python3 -u .cursor/hooks/aix_ingest.py"
```

## 环境变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `AIX_API_BASE` | AI-X REST 地址 | `http://127.0.0.1:8080` |
| `AIX_INGEST_TOKEN` | Bearer Token（与服务端一致） | UUID |
| `AIX_AUTO_END_SESSION` | `stop` 时是否结束会话 | `true` |
| `AIX_HTTP_TIMEOUT` | HTTP 超时秒数 | `3` |
| `AIX_HOOK_DEBUG` | 为 `true` 时在 stderr 输出诊断日志（Cursor 会显示为 Error Output） | 未设置 |

## 行为

- **fail-open**：采集失败不阻断 Cursor 对话
- **stdout** 固定输出 `{}`（Cursor 要求合法 JSON）
- **stderr** 仅在 API 失败或异常时输出；成功时不写 stderr，避免 Cursor 误标为 Error Output
- 需要排查时可设 `AIX_HOOK_DEBUG=true` 查看完整诊断日志
- Windows 下若 stdin JSON 损坏，脚本会从 `transcript_path` JSONL 读取正文（Cursor 已知问题 workaround）
- 字段映射见 [cursor-hooks-tech-options.md](../../docs/cursor-hooks-tech-options.md) §6.2

## 联调检查

1. AI-X 已启动：`GET /api/health` 返回 200
2. Token 与服务端 `AIX_INGEST_TOKEN` 一致
3. 发送测试消息后 MySQL `chat_message` 表有新记录

## Windows 说明

- `hooks.json` 使用 `python -u .cursor/hooks/aix_ingest.py`（无需 PowerShell 脚本）
- 若 Cursor 找不到 `python`，改为 Python 完整路径
- 可选：安装 PowerShell 7（`pwsh` 在 PATH）或开启系统 UTF-8，可减轻 stdin 编码问题
