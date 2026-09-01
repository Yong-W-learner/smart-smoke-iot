# 基于物联网与无人机协同的森林公园火险及生态资源守护系统

以智能烟感系统为基础，面向**森林公园火险监测**场景打造的端云协同守护系统。
森林监测节点持续感知火险，无人机到场复核，巡护员现场确认，生态资源分级守护。

所有评分均为**透明规则计算，不涉及 AI**：火险可信度、气象评分、生态影响评分、
事件优先级均可逐项回溯判定依据。

## 场景概览

- **4 个森林分区**：Z01 北部核心保护区 / Z02 东部防火通道区 / Z03 西部生态保育区 / Z04 南部生态涵养区
- **90 个监测节点**：设备 1001 = 真实硬件节点（FS-N-001）；1002~1090 = DEMO 模拟节点（FS-N-002~FS-N-090）
- **两类角色**：
  - `admin` → 森林生态安全指挥台（admin.html）
  - `resident` → 内部角色。绑定分区（`zone_id>0`）的 resident 即**巡护员**，
    登录移动巡护端（index.html）仅可见本人绑定分区数据；
    未绑定分区的 resident 保留为普通居民账号（兼容历史数据查看）
- **事件闭环**：烟雾判定 → FOREST 火险事件 → 无人机复核 → 巡护员现场反馈 → 生态回访 → 归档

## 核心功能

### 火险感知与判定
- 烟雾浓度实时采集、动态背景基线与持续异常判断
- 烟雾报警得分（0-100）与云端判定（NORMAL / WARNING / ALARM）
- 火险可信度评分：烟雾证据分 ×0.7 + 云端判定 + 边缘端状态 + 无人机复核加分，封顶 100
- 火险气象评分：温度贡献 0~30 + 相对湿度贡献 0~30 + 土壤湿度贡献 0~40，合计 0~100
- 事件优先级：火险×0.40 + 气象×0.25 + 古树影响 + 栖息地影响；无人机确认火点后强制 ≥95（RED）

### 生态资源守护
- 古树（ancient_tree）巡护检查与健康状态记录
- 野生动物栖息地（wildlife_habitat）分级标注
- 生态影响评分：事件点与古树 / 栖息地距离（Haversine 球面距离）按**分档**计分
  （古树 ≤100m 一级 20 / 二级 15 / 三级 10，100~250m 一级 12 / 二级 8 / 三级 5，
  >250m 记 0；栖息地 ≤200m CORE 15 / HIGH 10 / MEDIUM 5，>200m 记 0）；
  邻近分区资源按真实距离参与计分，不受分区限制
- 生态回访任务（ecological_followup）：FOREST 事件恢复后自动为影响资源生成回访任务

### 无人机协同
- 无人机任务状态机：PLANNED → DISPATCHED → EN_ROUTE → ON_SITE → RETURNED → COMPLETED
- 到达现场后确认火点 → 事件 `drone_confirmed=1`、优先级强制 ≥95，全程记录时间线

### 巡护协同
- 巡护员按分区授权（zone_id），移动端仅展示本人巡护区数据
- 现场反馈类型：`FIRE_CONFIRMED` / `NO_ABNORMALITY` / `SMOKE_UNCERTAIN` / `OTHER`
- FOREST 事件在环境未恢复时即可提交现场复核结果

### 指挥台（admin）
- 森林生态地图（SVG 分区 / 节点 / 古树 / 栖息地 / 火点事件）
- 活动事件队列（按优先级排序）、事件详情与无人机派发
- 人员管理（含巡护员账号创建与分区绑定）
- 气象看板、节点健康、生态资源与回访管理

## 技术栈

- Java 17
- Spring Boot 2.7.x
- Spring Security + JWT
- MyBatis-Plus
- MySQL（`smoke_db`）
- Huawei Cloud IoTDA
- HTML / CSS / JavaScript
- BearPi-HM Nano + E53_SF1（真实节点）

## 评分规则（透明规则，非 AI）

| 指标 | 公式 | 上限 |
|------|------|------|
| 火险可信度 | 烟雾证据分×0.7 + 云端 ALARM +15 + 边缘 ALARM +5 + 无人机确认 +20 | 100 |
| 火险气象 | 温度贡献 0~30 + 湿度贡献 0~30 + 土壤湿度贡献 0~40 | 100 |
| 古树影响 | ≤100m：一级20 / 二级15 / 三级10；100~250m：一级12 / 二级8 / 三级5；>250m：0 | 20 |
| 栖息地影响 | ≤200m：CORE 15 / HIGH 10 / MEDIUM 5；>200m：0 | 15 |
| 事件优先级 | 火险×0.40 + 气象×0.25 + 古树影响 + 栖息地影响；无人机确认 ≥95 | 100 |
| 优先级等级 | RED ≥80 / ORANGE ≥60 / YELLOW ≥40 / LOW <40 | - |

## 本地部署（第一次接触项目也能完成）

下面以 Windows 为主，macOS / Linux 的差异命令也已标出。整个项目不需要单独启动前端：HTML、CSS 和 JavaScript 会随 Spring Boot 一起运行。

### 1. 准备软件

请先安装：

- **Git**：用于下载代码。在终端执行 `git --version` 能看到版本号即安装成功。
- **JDK 17**：建议使用 Temurin 17。在终端执行 `java -version`，输出中应包含 `17`。
- **MySQL 8.x**：安装时请记住自己设置的 `root` 密码，并保证 MySQL 服务已启动。
- **IDEA（可选）**：不使用 IDEA 也能通过项目自带的 Maven Wrapper 启动。

> 不必另装 Maven，仓库中的 `mvnw.cmd` / `mvnw` 会自动使用指定版本。第一次构建需要联网下载依赖。

### 2. 下载正确分支

打开 PowerShell、终端或 Git Bash，执行：

```bash
git clone -b forest-v2 https://github.com/Yong-W-learner/smart-smoke-iot.git
cd smart-smoke-iot
```

如果已经下载过仓库，请确认当前分支：

```bash
git branch --show-current
```

输出应为 `forest-v2`。

### 3. 创建并初始化数据库

方法 A（推荐，命令行）：在项目根目录执行下面的命令，并按提示输入 MySQL 的 `root` 密码。

```bash
mysql -u root -p < sql/init.sql
```

Windows PowerShell 如果不支持上面的重定向写法，可执行：

```powershell
cmd /c "mysql -u root -p < sql\init.sql"
```

方法 B（图形界面）：使用 MySQL Workbench 连接本机数据库，打开 `sql/init.sql`，点击闪电按钮执行全部 SQL。

脚本会创建 `smoke_db`、4 张核心表以及本地演示管理员。其余森林分区、节点、古树、栖息地等表和演示数据会在程序第一次启动时自动创建，重复执行不会清空已有数据。

### 4. 配置本机密码

公开的 `application.yml` 不保存密码和云平台密钥。先复制一份只在本机使用的配置：

Windows PowerShell：

```powershell
Copy-Item src/main/resources/application.yml src/main/resources/application-private.yml
```

macOS / Linux：

```bash
cp src/main/resources/application.yml src/main/resources/application-private.yml
```

打开 `src/main/resources/application-private.yml`，将数据库密码占位符改成安装 MySQL 时设置的真实密码：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/smoke_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的MySQL密码
```

`application-private.yml` 已被 `.gitignore` 排除，不会随正常的 Git 提交上传。如果 MySQL 使用的不是 `3306` 端口，也要同步修改 URL 中的端口。

> 只体验网页和 DEMO 模拟节点时，不需要填写华为云配置。接入真实设备时，再把同一文件中的 `HUAWEI_IOT_*` 占位符替换为自己的 IoTDA 配置。不要把私密配置强制添加到 Git。

### 5. 启动项目

方式 A（推荐，命令行）：

Windows PowerShell / CMD：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

看到类似 `Started DemoApplication` 的日志即表示启动成功。终端窗口需要保持开启；按 `Ctrl + C` 可停止项目。

方式 B（IDEA）：

1. 用 IDEA 打开仓库根目录（包含 `pom.xml` 的目录）。
2. 等待右下角 Maven 依赖下载完成。
3. 打开 `src/main/java/com/example/demo/DemoApplication.java`。
4. 点击类旁边的绿色三角形，选择 **Run 'DemoApplication'**。

### 6. 打开系统并登录

浏览器访问：

```text
http://localhost:8080/login.html
```

| 身份 | 账号 | 初始密码 | 登录后页面 |
|------|------|----------|------------|
| 管理员 | `admin` | `123456` | 森林生态安全指挥台 |
| 巡护员 | `patrol` | `123456` | 移动巡护端（Z01 分区） |

账号首次成功登录后，程序会自动把明文初始密码升级为 BCrypt。自助注册已关闭；新的巡护员账号由管理员在指挥台中创建。

### 7. 快速验证部署是否成功

满足以下三点即可确认部署完成：

1. 启动日志出现 `Started DemoApplication`，且没有持续报错。
2. `http://localhost:8080/login.html` 能正常打开。
3. 使用 `admin / 123456` 登录后，指挥台能看到 4 个森林分区和监测节点。

### 常见问题

#### `java` 不是内部或外部命令 / `java: command not found`

JDK 17 未安装或环境变量未生效。重新安装 JDK 17，关闭并重新打开终端，再执行 `java -version`。

#### `Access denied for user 'root'@'localhost'`

`application.yml` 中的数据库用户名或密码不正确。使用你安装 MySQL 时设置的真实密码，不要照抄示例文字。

#### `Unknown database 'smoke_db'` 或 `Table ... doesn't exist`

数据库脚本没有成功执行。回到项目根目录，重新执行第 3 步的 `sql/init.sql`。

#### `Communications link failure`

MySQL 服务没有启动，或者端口不是 `3306`。Windows 可在“服务”中找到并启动 `MySQL80`；macOS / Linux 请用对应的 MySQL 服务管理命令。

#### `Port 8080 was already in use`

8080 端口已被其他程序占用。关闭占用该端口的程序，或把 `application.yml` 中的 `server.port` 改成其他端口，例如 `8081`，然后访问 `http://localhost:8081/login.html`。

#### Maven 下载依赖失败

确认网络可访问 Maven Central 后重试。若下载中断，可删除失败提示对应的单个依赖缓存后再次运行，不要删除整个项目。

#### 页面能打开，但真实硬件数据不可用

这不影响 DEMO 演示。真实设备功能需要自行准备华为云 IoTDA 实例、设备，并在 `application.yml` 与 `device.iot_device_id` 中填写自己的配置。

## 标准演示流程（设备 1002 · FS-N-002 · Z01）

1. 管理员登录指挥台，进入模拟测试：
   - `GET /api/test/scenario?deviceId=1002&scenario=RISING`（浓度持续上升）
   - `GET /api/test/smoke?deviceId=1002&value=60`（人工注入烟雾）
2. 云判定触发 FOREST 火险事件，自动计算火险可信度 / 气象评分 / 生态影响 / 事件优先级。
3. 指挥台为事件派发无人机 → 推进状态机至 ON_SITE → 确认火点（优先级强制 ≥95）。
4. `GET /api/test/scenario?deviceId=1002&scenario=RECOVERY`（浓度恢复）→ 事件自动标记恢复。
5. 巡护员（patrol / 123456，绑定 Z01）登录移动端，在本人分区内对事件提交现场复核反馈。
6. 事件恢复后自动生成生态回访任务 → 巡护员现场回访 → 完成归档。

> DEMO 环境气象任务每 30 秒为各分区生成新记录，驱动火险气象评分实时刷新。

## 临时公网访问

本地服务运行在 8080 端口时，可使用 cpolar 临时映射：

```bash
cpolar http 8080
```

以终端当次显示的 HTTPS `Forwarding` 地址为准。免费隧道重启后公网地址可能变化。

## 测试

```bash
./mvnw -o test
```

包含单元测试与场景测试共 165 项，覆盖森林火险评分、事件优先级、无人机任务状态机、
生态回访、巡护员数据权限、现场反馈、森林告警创建路径、节点状态组合口径（正常/离线/
传感器故障/数据过期与云端判定的交叉）、统一运维待办队列排序、DEMO 场景生成与模拟注入安全边界等。
历史宿舍业务测试全部保留，验证兼容数据不受影响。

## 兼容说明

- 历史宿舍数据（`building` / `floor` / `room`、历史告警、历史用户）完整保留；
  历史告警 `scene_type` 统一回填为 `DORM_LEGACY`，不参与森林评分。
- 设备 ID 1001~1090 沿用历史范围：1001 为真实硬件节点，1002~1090 为森林 DEMO 节点。
