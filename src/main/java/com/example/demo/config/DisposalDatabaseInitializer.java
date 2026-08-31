package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.List;

/**
 * 安全事件处置闭环 数据库升级（幂等）。
 *
 * 1. 为 alarm 表追加人工处置字段：
 *    disposal_state / handled_by / confirmed_at
 *    / on_site_at / close_at / disposal_remark
 *    处置状态与 recoverTime（环境恢复）相互独立。
 *
 * 2. 创建 alarm_event_log 表：
 *    记录"系统发现异常 → 创建告警 → 确认 → 到场
 *    → 填写处置结果 → 烟雾恢复 → 事件关闭"完整时间线。
 *
 * 每次启动自动执行，列已存在 / 表已存在时跳过，可重复运行。
 */
@Component
public class DisposalDatabaseInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(DisposalDatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DisposalDatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {

        addColumnIfMissing(
                "alarm",
                "disposal_state",
                "VARCHAR(20) NULL"
        );

        addColumnIfMissing(
                "alarm",
                "handled_by",
                "VARCHAR(64) NULL"
        );

        addColumnIfMissing(
                "alarm",
                "confirmed_at",
                "DATETIME NULL"
        );

        addColumnIfMissing(
                "alarm",
                "on_site_at",
                "DATETIME NULL"
        );

        addColumnIfMissing(
                "alarm",
                "close_at",
                "DATETIME NULL"
        );

        addColumnIfMissing(
                "alarm",
                "disposal_remark",
                "VARCHAR(500) NULL"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS alarm_event_log ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "alarm_id BIGINT NOT NULL,"
                        + "device_id BIGINT NOT NULL,"
                        + "event_type VARCHAR(32) NOT NULL,"
                        + "event_label VARCHAR(64) NOT NULL,"
                        + "description VARCHAR(255) NULL,"
                        + "operator VARCHAR(64) NULL,"
                        + "event_time DATETIME NOT NULL,"
                        + "KEY idx_aee_alarm_id (alarm_id),"
                        + "KEY idx_aee_device_id (device_id),"
                        + "KEY idx_aee_time (event_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        backfillDisposalStates();

        log.info(
                "安全事件处置闭环数据库升级完成："
                        + "alarm 表已追加处置字段，alarm_event_log 表已就绪，"
                        + "历史告警 disposal_state 已按优先级回填"
        );
    }

    /**
     * 历史告警 disposal_state 回填（幂等，按优先级有序）。
     *
     * 旧版本只有 acknowledged / ack_time / confirmed_at / on_site_at /
     * close_at / disposal_remark 字段，没有 disposal_state。
     * 前端把 disposal_state == null 归一化为 NEW，
     * 与历史 acknowledged=1 语义冲突，导致"确认按钮无变化"。
     *
     * 这里按处置进度最高档回填，优先级：
     * CLOSED &gt; RESOLVED &gt; ON_SITE &gt; ACKNOWLEDGED &gt; NEW。
     *
     * 每条 UPDATE 只处理 disposal_state 为空的历史行，
     * 后一步不会覆盖前一步已回填的状态。
     */
    private void backfillDisposalStates() {

        for (String sql : disposalStateBackfillSqls()) {

            int updated = jdbcTemplate.update(sql);

            if (updated > 0) {
                log.info(
                        "历史告警处置状态回填 {} 条：{}",
                        updated,
                        sql
                );
            }
        }
    }

    /**
     * 生成 disposal_state 回填 SQL（纯函数，便于单元测试）。
     */
    static List<String> disposalStateBackfillSqls() {

        String empty = "(disposal_state IS NULL OR disposal_state = '')";

        return List.of(
                "UPDATE alarm SET disposal_state = 'CLOSED' WHERE "
                        + empty + " AND close_at IS NOT NULL",
                "UPDATE alarm SET disposal_state = 'RESOLVED' WHERE "
                        + empty + " AND close_at IS NULL "
                        + "AND disposal_remark IS NOT NULL AND disposal_remark <> '' "
                        + "AND on_site_at IS NOT NULL",
                "UPDATE alarm SET disposal_state = 'ON_SITE' WHERE "
                        + empty + " AND close_at IS NULL AND on_site_at IS NOT NULL",
                "UPDATE alarm SET disposal_state = 'ACKNOWLEDGED' WHERE "
                        + empty + " AND close_at IS NULL "
                        + "AND (acknowledged = 1 OR ack_time IS NOT NULL "
                        + "OR confirmed_at IS NOT NULL)",
                "UPDATE alarm SET disposal_state = 'NEW' WHERE "
                        + empty
        );
    }

    private void addColumnIfMissing(
            String tableName,
            String columnName,
            String columnDefinition) {

        if (columnExists(tableName, columnName)) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE " + tableName
                        + " ADD COLUMN " + columnName
                        + " " + columnDefinition
        );

        log.info(
                "已为 {} 表补充列 {}",
                tableName,
                columnName
        );
    }

    private boolean columnExists(
            String tableName,
            String columnName) {

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() "
                                + "AND TABLE_NAME = ? "
                                + "AND COLUMN_NAME = ?",
                        Integer.class,
                        tableName,
                        columnName
                );

        return count != null && count > 0;
    }
}
