CREATE DATABASE IF NOT EXISTS smoke_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smoke_db;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    building INT NULL,
    floor INT NULL,
    room INT NULL,
    zone_id BIGINT NULL,
    phone VARCHAR(32) NULL,
    job_num VARCHAR(64) NULL,
    password VARCHAR(255) NOT NULL,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS device (
    device_id BIGINT PRIMARY KEY,
    building INT NULL,
    floor INT NULL,
    room INT NULL,
    zone_id BIGINT NULL,
    node_code VARCHAR(32) NULL,
    node_name VARCHAR(64) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    map_x DOUBLE NULL,
    map_y DOUBLE NULL,
    status INT NOT NULL DEFAULT 0,
    user_id BIGINT NULL,
    health_status VARCHAR(32) NULL,
    last_report_time DATETIME NULL,
    consecutive_failures INT NOT NULL DEFAULT 0,
    iot_device_id VARCHAR(128) NULL,
    source_type VARCHAR(16) NOT NULL DEFAULT 'REAL'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS smoke_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    collect_time DATETIME NOT NULL,
    smoke_concentration DOUBLE NULL,
    alarm INT NOT NULL DEFAULT 0,
    edge_state VARCHAR(32) NULL,
    edge_baseline DOUBLE NULL,
    smoke_ratio DOUBLE NULL,
    cloud_state VARCHAR(32) NULL,
    risk_score DOUBLE NULL,
    decision_reason VARCHAR(255) NULL,
    KEY idx_smoke_device_time (device_id, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alarm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    alarm_time DATETIME NOT NULL,
    location VARCHAR(255) NULL,
    acknowledged INT NOT NULL DEFAULT 0,
    ack_time DATETIME NULL,
    alarm_type VARCHAR(32) NULL,
    alarm_level VARCHAR(32) NULL,
    reason VARCHAR(255) NULL,
    recover_time DATETIME NULL,
    disposal_state VARCHAR(32) NULL,
    handled_by VARCHAR(64) NULL,
    confirmed_at DATETIME NULL,
    on_site_at DATETIME NULL,
    close_at DATETIME NULL,
    disposal_remark VARCHAR(500) NULL,
    scene_type VARCHAR(16) NULL,
    zone_id BIGINT NULL,
    fire_confidence_score INT NULL,
    fire_weather_score INT NULL,
    ancient_tree_impact_score INT NULL,
    wildlife_impact_score INT NULL,
    priority_score INT NULL,
    priority_level VARCHAR(16) NULL,
    priority_reason VARCHAR(255) NULL,
    drone_confirmed INT NOT NULL DEFAULT 0,
    KEY idx_alarm_device_time (device_id, alarm_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 仅用于本地演示。首次成功登录后，程序会把明文密码自动升级为 BCrypt。
INSERT INTO sys_user (username, password, role, phone, job_num)
SELECT 'admin', '123456', 'admin', '13800000000', 'ADMIN-001'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user WHERE username = 'admin'
);
