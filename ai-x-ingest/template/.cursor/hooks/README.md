# Cursor Hooks 安装说明

将本目录 `template/.cursor/` **复制到你的业务项目根目录**（不是 AI-X 服务端仓库）。

## 目录结构

```
your-project/
└── .cursor/
    ├── hooks.json
    └── hooks/
        ├── aix_ingest.py      # 主逻辑（跨平台）
        ├── aix-ingest.ps1     # Windows 入口
        └── README.md
```

macOS / Linux 可将 `hooks.json` 中的 command 改为：

```json
"command": ".cursor/hooks/aix_ingest.py"
```

并确保脚本可执行：`chmod +x .cursor/hooks/aix_ingest.py`

## 环境变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `AIX_API_BASE` | AI-X REST 地址 | `http://127.0.0.1:8080` |
| `AIX_INGEST_TOKEN` | Bearer Token（与服务端一致） | UUID |
| `AIX_AUTO_END_SESSION` | `stop` 时是否结束会话 | `true` |
| `AIX_HTTP_TIMEOUT` | HTTP 超时秒数 | `3` |

## 行为

- **fail-open**：采集失败不阻断 Cursor 对话
- 日志输出到 stderr，可在 Cursor Hooks 输出通道查看
- 字段映射见 [cursor-hooks-tech-options.md](../../docs/cursor-hooks-tech-options.md) §6.2

## 联调检查

1. AI-X 已启动：`GET /api/health` 返回 200
2. Token 与服务端 `AIX_INGEST_TOKEN` 一致
3. 发送测试消息后 MySQL `chat_message` 表有新记录
