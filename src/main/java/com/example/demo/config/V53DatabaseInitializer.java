package com.example.demo.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * V5.3 轻量数据库升级。
 *
 * 为了让用户直接打开新版本即可运行，这里只创建一张独立的新表，
 * 不修改现有 alarm / smoke_record / device 等核心表结构。
 */
@Component
public class V53DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public V53DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS alarm_feedback (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "alarm_id BIGINT NOT NULL," +
                        "user_id BIGINT NULL," +
                        "feedback_type VARCHAR(32) NOT NULL," +
                        "feedback_note VARCHAR(255) NULL," +
                        "feedback_time DATETIME NOT NULL," +
                        "UNIQUE KEY uk_alarm_feedback_alarm_id (alarm_id)," +
                        "KEY idx_alarm_feedback_user_id (user_id)," +
                        "KEY idx_alarm_feedback_time (feedback_time)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }
}
