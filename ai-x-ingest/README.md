# ai-x-ingest

Cursor Hooks 采集模板，供集成方复制到**业务项目**使用。

## 安装

```powershell
# Windows — 在业务项目根目录执行
Copy-Item -Recurse -Force template\.cursor .cursor
```

```bash
# macOS / Linux
cp -R template/.cursor .cursor
chmod +x .cursor/hooks/aix_ingest.py
```

配置环境变量后重启 Cursor，详见 `template/.cursor/hooks/README.md`。

## 设计说明

- Hook 通过 HTTP POST 调用 AI-X Ingestion REST API
- 与 MCP `record_message` 共用服务端 `ChatRecordService`
- 失败策略：**fail-open**（见技术文档 §4.4）

## 参考

- [docs/cursor-hooks-tech-options.md](../docs/cursor-hooks-tech-options.md)
