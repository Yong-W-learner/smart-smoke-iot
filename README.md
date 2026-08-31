# 森林景区火灾预警处置系统

软件综合设计项目，采用前后端分离架构。

- `demo/`：Spring Boot 后端，MySQL 数据库，接入华为云 IoTDA。
- `smart-smoke/`：Vue 3 + Vite 前端，包含护林员桌面端、移动端和游客预警页面。

真实数据库、华为云 IoTDA、高德地图和大模型凭据均通过环境变量配置，不提交到仓库。

## 本地启动

后端进入 `demo/`，配置数据库及所需环境变量后运行：

```bash
./mvnw spring-boot:run
```

前端复制 `smart-smoke/.env.example` 为 `smart-smoke/.env.local` 并填写高德地图配置，然后运行：

```bash
cd smart-smoke
npm install
npm run dev
```
