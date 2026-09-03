# 森林景区火灾预警处置系统

面向森林景区的火灾预警与巡护平台，提供游客预警大屏、护林员桌面端/移动端、森林传感器监测、无人机巡护、设备运维和摄像头 AI 复核。

## 技术架构

```text
浏览器
  └─ Nginx / Vue 3（80）
       └─ /api → Spring Boot（8080）
                    ├─ MySQL 8
                    ├─ 华为云 IoTDA（可选真机）
                    ├─ YOLO 服务（可选）
                    └─ Ollama 本地大模型 + Qdrant 向量知识库（AI 助手，全部本地运行，无云端调用、无真实 API Key）
```

AI 不可用时原系统功能与火情报警流程完全不受影响。

| 目录 | 用途 |
|---|---|
| `smart-smoke/` | Vue 3 + Vite 前端 |
| `demo/` | Spring Boot 后端及数据库初始化脚本 |
| `deploy/` | Docker Compose、环境变量模板和本地启动脚本 |

## 推荐部署：Docker Desktop

### 1. 环境要求

- Windows/macOS：Docker Desktop
- Linux：Docker Engine + Docker Compose v2
- 建议至少 4 GB 可用内存
- 默认使用本机 `80` 端口

确认 Docker 可用：

```powershell
docker version
docker compose version
```

下文命令以 Windows PowerShell 为例。macOS/Linux 用户将路径中的 `\` 改为 `/`，并使用 `cp` 代替 `Copy-Item`。

### 2. 获取代码

```powershell
git clone https://github.com/Yong-W-learner/smart-smoke-iot.git
cd smart-smoke-iot
git switch forest
```

如果已经下载代码，直接进入仓库根目录即可。

### 3. 创建安全配置

```powershell
Copy-Item .\deploy\.env.example .\deploy\.env.local
notepad .\deploy\.env.local
```

至少修改数据库密码：

```dotenv
DB_PASSWORD=请设置一个数据库密码
```

各配置项用途：

| 配置 | 是否必填 | 说明 |
|---|---:|---|
| `DB_PASSWORD` | 是 | Docker 内 MySQL root 密码 |
| `DB_URL/DB_USERNAME/SERVER_PORT` | 本地开发需要 | Docker 部署时由 Compose 自动覆盖 |
| `WEB_PORT` | 否 | 网页端口，默认 `80` |
| `VITE_AMAP_KEY` | 地图需要 | 高德开放平台 Web 端（JS API）Key |
| `VITE_AMAP_SECURITY_CODE` | 地图需要 | 与 Key 配套的 `securityJsCode` |
| `HUAWEI_IOT_AK/SK` | 真机需要 | 华为云访问密钥 |
| `HUAWEI_IOT_REGION_ID` | 真机需要 | IoTDA 区域，默认 `cn-north-4` |
| `HUAWEI_IOT_ENDPOINT` | 真机需要 | IoTDA 接入地址 |
| `HUAWEI_IOT_PROJECT_ID` | 真机需要 | 华为云项目 ID |
| `HUAWEI_IOT_DEVICE_ID` | 真机需要 | 烟感设备 ID |
| `YOLO_SERVICE_URL` | 否 | 摄像头识别服务；缺失时使用浓度规则判定 |
| `FOREST_AI_ENABLED` | 否 | 本地 AI 助手总开关，默认 `true`；`false` 可整体关闭 |
| `FOREST_LLM_BASE_URL/URL/KEY/MODEL` | 否 | 本地 Ollama OpenAI 兼容接口；Key 固定填占位符 `ollama`，不是真实密钥 |
| `FOREST_EMBEDDING_MODEL/FOREST_QDRANT_URL` | 否 | 本地嵌入模型与向量库地址 |
| `FOREST_AI_TIMEOUT_SECONDS/TOP_K/MAX_CONTEXT_MESSAGES` | 否 | AI 超时/检索条数/上下文轮数 |

高德地图 Key 必须选择“Web端（JS API）”，新 Key 需要同时填写安全密钥。真实密钥只放在 `deploy/.env.local`，该文件已被 Git 忽略。

如果 YOLO 服务运行在 Windows/macOS 宿主机，Docker 部署时将地址写成 `http://host.docker.internal:8001/detect`；本地非 Docker 开发使用 `http://127.0.0.1:8001/detect`。

### 4. 构建并启动

在仓库根目录运行（CPU 模式，任何机器都能启动）：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml up -d --build
```

有 NVIDIA GPU（建议 ≥ 6GB 显存，如 RTX 4060 Laptop 8GB）时追加 GPU 覆盖文件：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml -f .\deploy\compose.gpu.yml up -d --build
```

首次构建需要下载基础镜像和依赖，完成后查看状态：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml ps
```

核心服务 `mysql`、`backend`、`frontend` 应处于 `Up` 状态（MySQL 显示 `healthy`）；`ollama`、`qdrant` 为 AI 助手依赖，即使异常也不影响前三者。`ollama-init` 是一次性任务，首次会显示 `Exited (0)`。

### 5. 访问系统

| 页面 | 地址 |
|---|---|
| 游客预警大屏 | [http://localhost](http://localhost) |
| 护林员登录 | [http://localhost/login](http://localhost/login) |
| 后端接口验证 | [http://localhost/api/forest/bootstrap](http://localhost/api/forest/bootstrap) |

演示账号（课程演示级，公网部署前请删除或改密）：

```text
护林员：ranger / ranger123
管理员：admin / admin123（可维护 AI 知识库）
```

若修改了 `WEB_PORT`，例如 `WEB_PORT=8088`，访问地址相应改为 `http://localhost:8088`。

## 本地 AI 助手（森林安全智能助手）

### 功能

登录护林员后打开侧边菜单「森林安全AI助手」（或访问 `/ranger/ai`）：

- 基于 `knowledge/` 知识库（部署、接口、设备、告警阈值、巡护流程等）回答项目问题，并列出来源文档。
- 通过后端**只读白名单工具**查询实时业务数据：未结案火情、事件详情、设备状态与统计、传感器聚合历史、高风险设备、无人机任务、天气与火险、巡护汇总与报告草稿。
- 生成巡护总结、事件分析、日报；回答区分“知识库事实 / 实时数据事实 / 模型推断”。
- 建议操作按钮只跳转现有页面。AI 没有任何写权限：不能修改设备、删除数据、关闭或降级告警；火情等高风险结论一律提示人工复核。
- 会话保存于 MySQL（`ai_conversation`/`ai_message`），对话与工具调用写入 `ai_audit_log` 审计（参数脱敏，不保存密码/Token/Cookie/API Key）。

### 硬件与模型

| 项 | 值 |
|---|---|
| 对话模型 | `qwen3:4b`（约 2.5GB） |
| 嵌入模型 | `qwen3-embedding:0.6b`（约 640MB） |
| 显存建议 | 8GB 独立显卡（如 RTX 4060 Laptop）；CPU 也可运行，回答变慢 |
| 内存建议 | Docker Desktop 分配 ≥ 6GB |

Docker Desktop 要求：启用 WSL2 后端；使用 GPU 时安装 NVIDIA 驱动（592+）并在 Docker Desktop 设置中保持 GPU support 开启。

### 首次模型下载

`ollama-init` 服务会在 Ollama 健康后自动执行两条 `ollama pull`（已存在则秒过、不重复下载）。
网络受限导致失败时，容器会留下提示，可稍后手动补拉：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml exec ollama ollama pull qwen3:4b
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml exec ollama ollama pull qwen3-embedding:0.6b
```

### 健康检查

浏览器或 PowerShell 访问（经前端 Nginx 转发，无需登录）：

```powershell
Invoke-RestMethod http://localhost/api/forest/ai/health | ConvertTo-Json -Depth 5
```

返回 `ollamaReachable / chatModelExists / embeddingModelExists / qdrantReachable / knowledgePoints / degraded / degradedReasons`。模型缺失时 `degradedReasons` 会直接给出需要执行的 `ollama pull` 命令。

### 知识库

- 源目录：仓库根 `knowledge/`（容器内只读挂载）。放新的 `.md/.txt` 文档后，用管理员账号在 AI 页面点「重建知识库」，或：`POST /api/forest/ai/knowledge/reindex`（仅 admin）。
- 管理员页面还可「导入文档」（`.md/.txt/.pdf`，≤5MB），保存在独立数据卷，不改仓库。
- 按标题/自然段切为 400~800 字块（约 100 字重叠），`content_hash` 未变的文档自动跳过；文档删除/修改会同步清理旧向量。
- `.env`、密钥、日志、`node_modules`、`target`、`dist`、二进制等永远不会被索引。
- 缺少正式消防预案时仓库内只有标注“需管理员补充”的模板，AI 不会编造规范条款。

### 停止 / 重启 / 卸载

```powershell
# 停止（保留全部数据）
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml down
# 重新启动
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml up -d
# 完全卸载 AI 数据（危险：将删除模型权重 forest_ollama_data、
# 向量库 forest_qdrant_data、上传文档 forest_ai_uploads；
# 若一并删除 forest_mysql_data 还会清空业务与对话/审计数据库）
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml down
docker volume rm deploy_forest_ollama_data deploy_forest_qdrant_data deploy_forest_ai_uploads
```

### CPU / GPU 回退

默认 `compose.yml` 纯 CPU，保证任何机器可启动；带 `-f compose.gpu.yml` 时 Ollama 使用 NVIDIA GPU。若 GPU 覆盖导致容器起不来，去掉该文件重启即可，模型与知识库数据不受影响。

### 常见问题

| 现象 | 处理 |
|---|---|
| health 显示 `chatModelExists=false` | 执行上面两条 `ollama pull`；ollama-init 日志：`docker compose logs ollama-init` |
| 首次提问很慢 | 模型冷加载进显存，30 分钟内保持常驻（`OLLAMA_KEEP_ALIVE=30m`） |
| AI 页面提示“模型不可用” | 回答会自动降级为系统直接查询的实时数据摘要，业务功能不受影响 |
| 知识检索为空 | 确认 `knowledgePoints>0`；管理员点「重建知识库」 |
| 普通用户点重建返回 403 | 预期行为，重建/导入仅 admin |

### 安全与权限说明

- AI、Ollama、Qdrant 均不映射宿主端口，仅 compose 内部网络可达；`FOREST_LLM_API_KEY=ollama` 只是协议占位符。
- 对话只能访问当前登录用户的数据；清除会话仅限本人；知识库导入/重建仅限 admin（后端强制校验，非前端隐藏）。
- 模型不能生成/执行 SQL，不能指定类名方法名，只能触发白名单只读工具；知识库文本按“不可信引用”处理，不能覆盖系统提示词。
- 限流：单用户每分钟 10 次、输入 ≤2000 字、模型并发 2、超时默认 120 秒；接口不输出堆栈、连接串与绝对路径。
- 可用 `FOREST_AI_ENABLED=false` 整体关闭 AI；巡护总结在模型不可用时自动采用规则总结。

## 日常管理

以下命令均在仓库根目录运行。

启动已有容器：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml up -d
```

查看实时日志：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml logs -f
```

重启服务：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml restart
```

停止并移除容器（保留数据库）：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml down
```

更新代码后重新部署：

```powershell
git pull origin forest
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml up -d --build
```

前端的 `VITE_*` 配置会写入构建产物，修改高德 Key 后必须重新执行带 `--build` 的启动命令。

## 数据与配置安全

- MySQL 数据保存在 Docker Volume `forest_mysql_data` 中，重建容器不会清空数据。
- `docker compose down` 会保留数据；`docker compose down -v` 会永久删除数据库，请谨慎使用。
- 不要提交 `deploy/.env.local`、`smart-smoke/.env.local` 或任何真实 AK/SK、API Key。
- 可用以下命令确认配置文件被 Git 忽略：

```powershell
git check-ignore -v .\deploy\.env.local
```

## 不使用 Docker 的本地开发

需要预先安装 Java 8、MySQL 8 和 Node.js 22，并创建数据库 `smoke_db`。

1. 按前述方式准备 `deploy/.env.local`，将 `DB_URL` 指向本机 MySQL。
2. 在第一个 PowerShell 窗口启动后端：

   ```powershell
   .\deploy\run-backend.ps1
   ```

3. 在第二个 PowerShell 窗口启动前端：

   ```powershell
   .\deploy\run-frontend.ps1
   ```

4. 根据 Vite 输出的地址访问，默认通常为 `http://localhost:5173`。

## 常见问题

### 端口 80 被占用

在 `deploy/.env.local` 中修改：

```dotenv
WEB_PORT=8088
```

然后重新运行 `docker compose ... up -d`，访问 `http://localhost:8088`。

### 页面能打开，但地图空白

依次检查：

1. Key 的平台是否为“Web端（JS API）”。
2. `VITE_AMAP_KEY` 和 `VITE_AMAP_SECURITY_CODE` 是否属于同一个 Key。
3. 高德控制台域名白名单是否允许当前域名或 `localhost`。
4. 修改配置后是否执行了 `up -d --build`。

### Docker 拉取镜像超时

项目已使用 DaoCloud 公共镜像代理。若仍超时，请确认 Docker Desktop 已启动，并检查系统代理、DNS、防火墙或校园网限制后重试构建命令。

### 后端或数据库启动失败

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml ps
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml logs --tail 200 backend mysql
```

重点检查 `DB_PASSWORD` 是否为空、MySQL 是否为 `healthy`，以及端口是否冲突。

## 公网部署提醒

当前版本适合课程演示和可信局域网使用。公开到互联网前，至少应增加 HTTPS、真实 JWT 鉴权、密码哈希、接口权限校验、受限 CORS、高德安全密钥服务端代理，以及数据库和云服务密钥轮换。
