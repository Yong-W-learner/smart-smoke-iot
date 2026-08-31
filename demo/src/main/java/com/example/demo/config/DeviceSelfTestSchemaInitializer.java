package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 为已有本地数据库补充自检记录表，不改动现有业务数据。 */
@Component
public class DeviceSelfTestSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DeviceSelfTestSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS device_self_test ("
                + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',"
                + "device_id BIGINT NOT NULL COMMENT '设备id',"
                + "operator_id BIGINT NOT NULL COMMENT '操作管理员id',"
                + "operator_name VARCHAR(64) NULL COMMENT '操作管理员',"
                + "test_time DATETIME NOT NULL COMMENT '自检时间',"
                + "online_ok TINYINT(1) NOT NULL DEFAULT 0 COMMENT '在线检查',"
                + "telemetry_ok TINYINT(1) NOT NULL DEFAULT 0 COMMENT '数据上报检查',"
                + "beep_command_ok TINYINT(1) NOT NULL DEFAULT 0 COMMENT '蜂鸣器命令下发',"
                + "beep_observed_ok TINYINT(1) NOT NULL DEFAULT 0 COMMENT '蜂鸣器现场确认',"
                + "led_command_ok TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'LED命令下发',"
                + "led_observed_ok TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'LED现场确认',"
                + "result VARCHAR(16) NOT NULL COMMENT 'passed通过/failed异常',"
                + "remark VARCHAR(512) NULL COMMENT '自检备注',"
                + "PRIMARY KEY (id), KEY idx_device_test_time (device_id, test_time)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备自检记录表'");
    }
}
