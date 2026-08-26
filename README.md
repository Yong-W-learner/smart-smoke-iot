# Smart Smoke · 智慧烟感系统

> 一个基于 **BearPi + Huawei Cloud IoTDA + Spring Boot + MySQL** 的端云协同智慧烟感原型系统。

Smart Smoke 面向 **居民端** 与 **管理员端** 两类用户，围绕烟雾采集、风险判断、告警处置、设备健康与远程控制构建完整链路。

当前项目主要用于学习、实训与原型验证，仍在持续迭代中。

---

## ✨ 核心功能

- **实时烟雾监测**：采集 MQ-2 烟雾浓度并展示趋势
- **动态环境基线**：根据正常环境数据缓慢更新背景值
- **误报抑制**：结合滑动窗口、持续异常与恢复迟滞，降低单点波动误报
- **端云协同**：BearPi 负责本地快速响应，Spring Boot 负责云端持续分析
- **风险评分**：将当前烟雾状态转换为 0~100 风险分
- **设备健康监测**：识别 NORMAL / STALE / OFFLINE / SENSOR_FAULT
- **告警生命周期**：触发 → 人工确认 → 环境恢复 → 事件复盘
- **居民 / 管理员权限隔离**：JWT + Spring Security + 房间级数据权限
- **远程控制**：管理员可通过 IoTDA 控制 LED 与蜂鸣器
- **安全优先策略**：真实高风险状态优先于人工关闭操作
- **事件反馈**：居民可反馈真实火情、烹饪烟雾、蒸汽等实际场景

---

## 🧱 技术栈

| 模块 | 技术 |
|---|---|
| 边缘设备 | BearPi-HM_Nano、E53_SF1、MQ-2、C |
| IoT 平台 | Huawei Cloud IoTDA、MQTT、Device Shadow |
| 后端 | Java 17、Spring Boot 2.7.15、Spring Security |
| 数据访问 | MyBatis-Plus |
| 数据库 | MySQL 8.x |
| 认证 | JWT、BCrypt |
| 前端 | HTML、CSS、JavaScript |
| 构建 | Maven |

---

## 🔗 系统链路

```text
MQ-2
  ↓
BearPi-HM_Nano
  ↓
Huawei Cloud IoTDA
  ↓
Spring Boot
  ↓
MySQL
  ↓
居民端 / 管理员端
```

边缘端负责快速判定与本地声光报警，云端负责持续性分析、风险评分、设备健康、告警管理和权限控制。

---

# 🚀 快速上手

## 1. 克隆项目

```bash
git clone https://github.com/Yong-W-learner/smart-smoke-iot.git
cd smart-smoke-iot
```

## 2. 环境要求

请准备：

```text
JDK 17
MySQL 8.x
Maven（或直接使用项目自带 Maven Wrapper）
```

真实 IoTDA 设备接入还需要：

```text
Huawei Cloud IoTDA
BearPi-HM_Nano + E53_SF1
```

---

## 3. 创建数据库

```sql
CREATE DATABASE smoke_db
CHARACTER SET utf8mb4;
```

当前版本使用的核心业务表包括：

```text
sys_user
device
smoke_record
alarm
alarm_feedback
```

> 数据库初始化脚本仍在完善中。

---

## 4. 配置环境变量

项目不会在源码中保存真实密码、AK/SK 或 JWT Secret。

需要配置：

```text
DB_PASSWORD
HUAWEI_IOT_AK
HUAWEI_IOT_SK
HUAWEI_IOT_ENDPOINT
HUAWEI_IOT_DEVICE_ID
HUAWEI_IOT_PROJECT_ID
JWT_SECRET
```

可选：

```text
DB_URL
DB_USERNAME
HUAWEI_IOT_REGION
JWT_EXPIRATION
```

Windows PowerShell 示例：

```powershell
$env:DB_PASSWORD="your_db_password"
$env:HUAWEI_IOT_AK="your_ak"
$env:HUAWEI_IOT_SK="your_sk"
$env:HUAWEI_IOT_ENDPOINT="your_iotda_endpoint"
$env:HUAWEI_IOT_DEVICE_ID="your_device_id"
$env:HUAWEI_IOT_PROJECT_ID="your_project_id"
$env:JWT_SECRET="replace_with_a_long_random_secret"
```

> 不要把真实密钥写入 README、Issue、截图或公开提交。

---

## 5. 启动后端

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

也可以直接在 IDEA 中运行：

```text
DemoApplication
```

启动成功后访问：

```text
http://localhost:8080/login.html
```

---

## 👥 两类用户

### 居民端

主要关注：

- 当前烟雾风险
- 实时烟雾趋势
- 设备在线状态
- 最近安全事件
- 告警确认与事件反馈

### 管理员端

主要用于：

- 用户管理
- 设备管理
- 告警处理
- 监测记录
- 设备健康
- LED / 蜂鸣器远程控制
- 多住户、多设备监控能力扩展

---

## 💡 项目特点

### 1. 动态基线，而不是只依赖固定阈值

系统会根据正常环境缓慢更新背景基线，并使用：

```text
当前烟雾值 / 环境基线
```

辅助判断异常程度。

### 2. 单点异常不直接等于火警

云端结合：

```text
滑动窗口
持续高值
风险评分
报警锁存
恢复迟滞
```

减少瞬时尖峰造成的误报。

### 3. 云端失联时仍保留边缘保护逻辑

BearPi 本地仍具备烟雾判断与声光报警策略，避免把安全能力完全依赖在网络和云端。

### 4. 不只监测烟雾，也监测设备本身

系统会区分：

```text
NORMAL
STALE
OFFLINE
SENSOR_FAULT
```

用于识别“设备在线但数据异常”等情况。

---

## 🤝 参与项目

欢迎提交：

- Issue
- Feature Request
- Bug Report
- Pull Request
- 文档改进

目前特别欢迎参与：

- 管理员多户设备监控矩阵
- 多设备决策上下文
- 消息 / 告警通知
- 设备自检
- 移动端体验优化
- 测试覆盖
- 数据库初始化脚本

---

## ⚠️ 免责声明

本项目目前用于 **学习、实训、研究与原型验证**。

它不是经过消防认证的专业安全设备，不应替代符合当地法规和标准的正式消防报警系统。

---

## 📄 License

MIT License

欢迎学习、修改与贡献。
