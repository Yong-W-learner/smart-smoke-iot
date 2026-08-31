package com.example.demo.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史告警 disposal_state 回填 SQL 的纯函数测试。
 *
 * 覆盖：
 * - 按处置进度最高档有序回填：CLOSED &gt; RESOLVED &gt; ON_SITE
 *   &gt; ACKNOWLEDGED &gt; NEW（后一步不覆盖前一步已回填的状态）。
 * - 每条 UPDATE 只处理 disposal_state 为空的历史行。
 * - ACKNOWLEDGED 分支覆盖 legacy 的
 *   acknowledged=1 / ack_time / confirmed_at 三种判定来源。
 * - RESOLVED 分支必须同时满足 disposal_remark 非空且 on_site_at 非空。
 */
public class DisposalDatabaseInitializerTest {

    @Test
    void backfillPriorityClosedFirstThenResolvedOnSiteAcknowledgedNew() {

        List<String> sqls = DisposalDatabaseInitializer.disposalStateBackfillSqls();

        assertEquals(5, sqls.size(), "应生成 5 条回填 SQL");

        assertTrue(sqls.get(0).startsWith("UPDATE alarm SET disposal_state = 'CLOSED'"),
                "优先级最高：close_at 非空 → CLOSED");
        assertTrue(sqls.get(1).startsWith("UPDATE alarm SET disposal_state = 'RESOLVED'"),
                "close_at 为空但已填处置结果且已到场 → RESOLVED");
        assertTrue(sqls.get(2).startsWith("UPDATE alarm SET disposal_state = 'ON_SITE'"),
                "已到场但未填处置结果 → ON_SITE");
        assertTrue(sqls.get(3).startsWith("UPDATE alarm SET disposal_state = 'ACKNOWLEDGED'"),
                "已确认但未到场 → ACKNOWLEDGED");
        assertTrue(sqls.get(4).startsWith("UPDATE alarm SET disposal_state = 'NEW'"),
                "最后兜底：全部条件不满足 → NEW");
    }

    @Test
    void acknowledgedSqlCoversAllLegacyConfirmationSources() {

        String ackedSql = DisposalDatabaseInitializer
                .disposalStateBackfillSqls()
                .get(3);

        assertTrue(ackedSql.contains("acknowledged = 1"),
                "legacy 已确认判定来源 1：acknowledged 字段");
        assertTrue(ackedSql.contains("ack_time IS NOT NULL"),
                "legacy 已确认判定来源 2：ack_time 非空");
        assertTrue(ackedSql.contains("confirmed_at IS NOT NULL"),
                "legacy 已确认判定来源 3：confirmed_at 非空");
    }

    @Test
    void everySqlOnlyTargetsEmptyDisposalState() {

        List<String> sqls = DisposalDatabaseInitializer.disposalStateBackfillSqls();

        String empty = "disposal_state IS NULL OR disposal_state = ''";

        for (String sql : sqls) {
            assertTrue(sql.contains(empty),
                    "每条回填 SQL 都必须只处理 disposal_state 为空的历史行：" + sql);
        }
    }

    @Test
    void resolvedSqlRequiresBothRemarkAndOnSite() {

        String resolvedSql = DisposalDatabaseInitializer
                .disposalStateBackfillSqls()
                .get(1);

        assertTrue(resolvedSql.contains("close_at IS NULL"),
                "已关闭的不能回填为 RESOLVED");
        assertTrue(resolvedSql.contains("disposal_remark IS NOT NULL"),
                "未填写处置结果不能回填为 RESOLVED");
        assertTrue(resolvedSql.contains("disposal_remark <> ''"),
                "处置结果为空字符串不能回填为 RESOLVED");
        assertTrue(resolvedSql.contains("on_site_at IS NOT NULL"),
                "未到场不能回填为 RESOLVED");
    }

    @Test
    void onSiteSqlExcludesClosedAlarms() {

        String onSiteSql = DisposalDatabaseInitializer
                .disposalStateBackfillSqls()
                .get(2);

        assertTrue(onSiteSql.contains("close_at IS NULL"),
                "已关闭的不能回填为 ON_SITE");
        assertTrue(onSiteSql.contains("on_site_at IS NOT NULL"),
                "已到场是 ON_SITE 的必要条件");
    }
}
