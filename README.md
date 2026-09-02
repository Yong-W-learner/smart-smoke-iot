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
                    └─ OpenAI 兼容大模型（可选）
```

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
| `FOREST_LLM_API_URL/KEY/MODEL` | 否 | OpenAI 兼容接口；缺失时使用本地规则总结 |

高德地图 Key 必须选择“Web端（JS API）”，新 Key 需要同时填写安全密钥。真实密钥只放在 `deploy/.env.local`，该文件已被 Git 忽略。

如果 YOLO 服务运行在 Windows/macOS 宿主机，Docker 部署时将地址写成 `http://host.docker.internal:8001/detect`；本地非 Docker 开发使用 `http://127.0.0.1:8001/detect`。

### 4. 构建并启动

在仓库根目录运行：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml up -d --build
```

首次构建需要下载基础镜像和依赖，完成后查看状态：

```powershell
docker compose --env-file .\deploy\.env.local -f .\deploy\compose.yml ps
```

三个服务应处于 `Up` 状态，MySQL 应显示 `healthy`。

### 5. 访问系统

| 页面 | 地址 |
|---|---|
| 游客预警大屏 | [http://localhost](http://localhost) |
| 护林员登录 | [http://localhost/login](http://localhost/login) |
| 后端接口验证 | [http://localhost/api/forest/bootstrap](http://localhost/api/forest/bootstrap) |

演示护林员账号：

```text
用户名：ranger
密码：ranger123
```

若修改了 `WEB_PORT`，例如 `WEB_PORT=8088`，访问地址相应改为 `http://localhost:8088`。

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
