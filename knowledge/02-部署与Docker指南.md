# 部署与 Docker 指南

## 前置要求

- Windows/macOS 安装 Docker Desktop（或 Linux Docker Engine + Compose v2），建议 ≥ 8GB Docker 内存。
- 本地 AI 助手需要 Ollama 加载 qwen3:4b（约 2.5GB）与 qwen3-embedding:0.6b（约 640MB）。
  8GB 独立显存的 NVIDIA GPU（如 RTX 4060 Laptop 8GB）可流畅运行 qwen3:4b；无 GPU 时自动回退 CPU，功能不变、速度变慢。

## 启动

```powershell
# 在仓库根目录
Copy-Item .\deploy\.env.local 已有则跳过
# CPU 模式（保证任何机器可启动）：
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml up -d --build
# 有 NVIDIA GPU 时追加 GPU 覆盖文件：
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml -f .\deploy\compose.gpu.yml up -d --build
```

## 服务清单

| 服务 | 镜像 | 说明 |
|---|---|---|
| mysql | mysql:8.0 | 端口不对外；数据卷 forest_mysql_data |
| backend | 本地构建 demo/Dockerfile | Spring Boot，8080，仅内部网络 |
| frontend | 本地构建 smart-smoke/Dockerfile | Nginx，宿主端口 WEB_PORT（默认 80） |
| ollama | ollama/ollama | 11434 不映射宿主端口，仅内部网络；数据卷 forest_ollama_data |
| ollama-init | ollama/ollama | 一次性任务：等待 ollama 健康后拉取 qwen3:4b 与 qwen3-embedding:0.6b，已有模型不重复下载 |
| qdrant | qdrant/qdrant | 6333 不映射宿主端口，仅内部网络；数据卷 forest_qdrant_data |

## 验证

- 前端：http://localhost（或 WEB_PORT 端口）
- 后端：http://localhost/api/forest/bootstrap
- AI 健康：http://localhost/api/forest/ai/health —— 返回 Ollama 可达、两个模型是否存在、Qdrant 可达、是否降级。

## 安全配置

真实密码放在 deploy/.env.local（已被 Git 忽略）。AI 相关变量见 .env.example：
FOREST_AI_ENABLED、FOREST_LLM_BASE_URL、FOREST_LLM_API_URL、FOREST_LLM_API_KEY（固定占位符
"ollama"，不是密钥）、FOREST_LLM_MODEL、FOREST_EMBEDDING_MODEL、FOREST_QDRANT_URL、
FOREST_AI_TIMEOUT_SECONDS、FOREST_AI_MAX_CONTEXT_MESSAGES、FOREST_AI_TOP_K。

## 降级保证

- Ollama 或 Qdrant 未启动/模型未下载：backend 正常启动，业务接口正常，AI 对话返回降级说明与实时数据摘要。
- FOREST_AI_ENABLED=false 可整体关闭 AI 功能。
- 巡护任务完成总结在模型不可用时自动使用规则总结（summary_source=rules）。
