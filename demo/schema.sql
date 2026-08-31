-- ============================================================
-- 福州国家森林公园防火巡护系统 · 建库建表脚本
-- 用法：装好 MySQL 后，在命令行执行：
--   mysql -u root -p < schema.sql
-- （会提示输入 root 密码，即 cyh09161106）
-- 或者用 Navicat / DataGrip / MySQL Workbench 打开后直接运行
-- ============================================================

CREATE DATABASE IF NOT EXISTS smoke_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE smoke_db;

-- 1. 用户表（当前森林景区业务仅使用护林员账号）
CREATE TABLE IF NOT EXISTS sys_user (
    id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(64)  NOT NULL COMMENT '用户名',
    role     VARCHAR(16)  NOT NULL DEFAULT 'ranger' COMMENT '角色 ranger护林员',
    building INT          NULL COMMENT '楼栋',
    floor    INT          NULL COMMENT '楼层',
    room     INT          NULL COMMENT '房间',
    phone    VARCHAR(20)  NULL COMMENT '手机号',
    job_num  VARCHAR(32)  NULL COMMENT '管理员工号（居民为NULL）',
    password VARCHAR(128) NOT NULL COMMENT '密码（当前为明文）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 设备表
CREATE TABLE IF NOT EXISTS device (
    device_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备主键',
    building  INT    NULL COMMENT '楼栋',
    floor     INT    NULL COMMENT '楼层',
    room      INT    NULL COMMENT '房间',
    status    INT    NOT NULL DEFAULT 0 COMMENT '0离线 1在线',
    user_id   BIGINT NULL COMMENT '归属居民id，关联sys_user.id',
    PRIMARY KEY (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 3. 烟雾采集记录表
CREATE TABLE IF NOT EXISTS smoke_record (
    id                 BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id          BIGINT   NOT NULL COMMENT '设备id',
    collect_time       DATETIME NOT NULL COMMENT '采集时间',
    smoke_concentration DOUBLE  NULL COMMENT '烟雾浓度(ppm)',
    alarm              INT      NOT NULL DEFAULT 0 COMMENT '0正常 1告警',
    PRIMARY KEY (id),
    KEY idx_device_time (device_id, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='烟雾采集记录表';

-- 4. 警情事件表（独立于设备运维工单）
CREATE TABLE IF NOT EXISTS alarm (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id  BIGINT       NOT NULL COMMENT '设备id',
    alarm_time DATETIME     NOT NULL COMMENT '告警时间',
    location   VARCHAR(128) NULL COMMENT '安装位置',
    status     INT          NOT NULL DEFAULT 0 COMMENT '0待处置 1已处置',
    process_status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/arrived/handled',
    confirmer_id BIGINT NULL COMMENT '确认管理员id',
    confirmer_name VARCHAR(64) NULL COMMENT '确认管理员',
    confirm_time DATETIME NULL COMMENT '管理员确认时间',
    handler_id BIGINT NULL COMMENT '应急消防员id',
    handler_name VARCHAR(64) NULL COMMENT '应急消防员',
    response_time DATETIME NULL COMMENT '兼容旧版首次响应时间',
    arrival_time DATETIME NULL COMMENT '人员到场时间',
    handle_time DATETIME NULL COMMENT '处置完成时间',
    handle_result VARCHAR(32) NULL COMMENT '现场处置结果',
    handle_remark VARCHAR(512) NULL COMMENT '现场情况和处置说明',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='警情事件表';

-- 5. 报警复核现场画面表（摄像头抓拍上传到管理员）
CREATE TABLE IF NOT EXISTS alarm_review (
    id                 BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id          BIGINT   NOT NULL COMMENT '设备id',
    alarm_level        INT      NOT NULL DEFAULT 0 COMMENT '警情等级 0正常/1一级/2二级/3三级',
    smoke_concentration DOUBLE  NULL COMMENT '抓拍时烟雾浓度(ppm)',
    image_base64       LONGTEXT NULL COMMENT '现场照片(base64编码)',
    create_time        DATETIME NOT NULL COMMENT '抓拍时间',
    ai_verdict         VARCHAR(32)  NULL COMMENT 'AI融合判定 normal/steam/smoke/fire',
    ai_basis           VARCHAR(512) NULL COMMENT 'AI判定依据',
    ai_detections      TEXT NULL COMMENT 'AI检测列表JSON [{label,conf}]',
    ai_boxes           TEXT NULL COMMENT 'AI检测框JSON [{x,y,w,h,label,conf}]百分比坐标',
    PRIMARY KEY (id),
    KEY idx_review_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警复核现场画面表';

-- 6. 设备运维工单表（居民报修，维修员接单；警情不进入本表）
CREATE TABLE IF NOT EXISTS work_order (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32)  NOT NULL COMMENT '工单编号',
    type          VARCHAR(16)  NOT NULL COMMENT '类型 repair设备报修；alarm仅兼容历史数据',
    title         VARCHAR(128) NOT NULL COMMENT '标题',
    description   VARCHAR(512) NULL COMMENT '描述',
    building      INT          NULL COMMENT '楼栋',
    floor         INT          NULL COMMENT '楼层',
    room          INT          NULL COMMENT '房间',
    device_id     BIGINT       NULL COMMENT '关联设备id（可选）',
    reporter_id   BIGINT       NULL COMMENT '报修居民id',
    reporter_name VARCHAR(64)  NULL COMMENT '报修居民姓名',
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending待接单/accepted已接单/closed已关闭',
    repairer_id   BIGINT       NULL COMMENT '接单维修员id',
    repairer_name VARCHAR(64)  NULL COMMENT '接单维修员姓名',
    remark        VARCHAR(512) NULL COMMENT '关闭备注',
    create_time   DATETIME     NOT NULL COMMENT '创建时间',
    accept_time   DATETIME     NULL COMMENT '接单时间',
    close_time    DATETIME     NULL COMMENT '关闭时间',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- 7. 设备自检记录表
CREATE TABLE IF NOT EXISTS device_self_test (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id         BIGINT       NOT NULL COMMENT '设备id',
    operator_id       BIGINT       NOT NULL COMMENT '操作管理员id',
    operator_name     VARCHAR(64)  NULL COMMENT '操作管理员',
    test_time         DATETIME     NOT NULL COMMENT '自检时间',
    online_ok         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '在线检查',
    telemetry_ok      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '数据上报检查',
    beep_command_ok   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '蜂鸣器命令下发',
    beep_observed_ok  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '蜂鸣器现场确认',
    led_command_ok    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'LED命令下发',
    led_observed_ok   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'LED现场确认',
    result            VARCHAR(16)  NOT NULL COMMENT 'passed通过/failed异常',
    remark            VARCHAR(512) NULL COMMENT '自检备注',
    PRIMARY KEY (id),
    KEY idx_device_test_time (device_id, test_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备自检记录表';

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认护林员账号：用户名 ranger / 密码 ranger123
INSERT INTO sys_user (username, role, password, job_num, phone)
VALUES ('ranger', 'ranger', 'ranger123', 'G0001', '13800000001')
ON DUPLICATE KEY UPDATE role = VALUES(role), job_num = VALUES(job_num), phone = VALUES(phone);

-- 一台示例设备（后端采集任务写死 device_id=1，先插一条对上）
INSERT INTO device (device_id, building, floor, room, status)
VALUES (1, 3, 8, 2, 1)
ON DUPLICATE KEY UPDATE device_id = device_id;

-- ============================================================
-- 完成。可执行以下语句自查：
--   SELECT * FROM sys_user;   -- 应能看到 ranger
--   SHOW TABLES;              -- 应有 7 张表
-- ============================================================
