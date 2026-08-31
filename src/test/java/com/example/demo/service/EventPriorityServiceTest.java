package com.example.demo.service;

import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 紧急事件优先级评分与风险级别推导测试。
 */
public class EventPriorityServiceTest {

    private final EventPriorityService service =
            new EventPriorityService();

    /**
     * 主序由基础分保证：
     * ALARM 即使风险分较低，
     * 仍应压过风险分明显更高的 WARNING。
     */
    @Test
    void alarmOutranksWarningEvenWithHigherRisk() {

        double alarm =
                service.priorityScore("ALARM", 30.0, 0, 0, 0);

        double warning =
                service.priorityScore("WARNING", 80.0, 0, 0, 0);

        assertEquals(69.0, alarm, 0.001);
        assertEquals(64.0, warning, 0.001);

        assertTrue(
                alarm > warning,
                "ALARM(69) 应压过 WARNING(64)"
        );
    }

    /**
     * 同状态同风险下，
     * 未确认告警的优先级更高。
     */
    @Test
    void unacknowledgedOutranksAcknowledged() {

        double unacked =
                service.priorityScore("WARNING", 50.0, 10, 1, 0);

        double acked =
                service.priorityScore("WARNING", 50.0, 10, 0, 0);

        assertTrue(
                unacked > acked,
                "未确认告警应更紧急"
        );

        assertEquals(66.5, unacked, 0.001);
        assertEquals(56.5, acked, 0.001);
    }

    /**
     * 风险分与持续时间越大，分数越高。
     */
    @Test
    void durationAndRiskBoostScore() {

        double lowRisk =
                service.priorityScore("ALARM", 10.0, 5, 0, 0);

        double highRisk =
                service.priorityScore("ALARM", 60.0, 5, 0, 0);

        assertTrue(
                highRisk > lowRisk,
                "风险分应提升优先级"
        );

        double shortAlarm =
                service.priorityScore("ALARM", 50.0, 1, 0, 0);

        double longAlarm =
                service.priorityScore("ALARM", 50.0, 40, 0, 0);

        assertTrue(
                longAlarm > shortAlarm,
                "持续越久应越紧急"
        );
    }

    /**
     * OFFLINE 比 STALE 更紧急。
     */
    @Test
    void offlineOutranksStale() {

        double offline =
                service.priorityScore("OFFLINE", 0.0, 0, 0, 0);

        double stale =
                service.priorityScore("STALE", 0.0, 0, 0, 0);

        assertTrue(
                offline > stale
        );
    }

    /**
     * 正常寝室优先级最低。
     */
    @Test
    void normalIsLowest() {

        double normal =
                service.priorityScore("NORMAL", 0.0, 0, 0, 0);

        double stale =
                service.priorityScore("STALE", 0.0, 0, 0, 0);

        assertEquals(0.0, normal, 0.001);

        assertTrue(
                normal < stale
        );
    }

    /**
     * 总分封顶 100。
     */
    @Test
    void scoreCappedAt100() {

        double score =
                service.priorityScore(
                        "ALARM",
                        100.0,
                        60,
                        1,
                        5
                );

        assertEquals(100.0, score, 0.001);
    }

    /* ==================================================
       riskLevel 推导
       ================================================== */

    @Test
    void healthStatusWinsOverCloudState() {

        Device offline = device("OFFLINE");
        SmokeRecord alarming = record("ALARM");

        assertEquals(
                "OFFLINE",
                service.riskLevel(offline, alarming)
        );

        Device stale = device("STALE");
        assertEquals(
                "STALE",
                service.riskLevel(stale, record("ALARM"))
        );
    }

    @Test
    void sensorFaultPrefersHealth() {

        Device sensorFault = device("SENSOR_FAULT");

        assertEquals(
                "SENSOR_FAULT",
                service.riskLevel(sensorFault, record("WARNING"))
        );
    }

    @Test
    void cloudStateDerivesRiskWhenHealthy() {

        Device healthy = device("NORMAL");

        assertEquals(
                "ALARM",
                service.riskLevel(healthy, record("ALARM"))
        );

        assertEquals(
                "WARNING",
                service.riskLevel(healthy, record("WARNING"))
        );

        assertEquals(
                "NORMAL",
                service.riskLevel(healthy, record("NORMAL"))
        );
    }

    @Test
    void noLatestIsNormal() {

        Device healthy = device("NORMAL");

        assertEquals(
                "NORMAL",
                service.riskLevel(healthy, null)
        );
    }

    /**
     * 存在活动告警时，
     * 即使设备随后 OFFLINE / STALE，
     * 风险级别仍必须保持 ALARM。
     */
    @Test
    void activeAlarmKeepsAlarmEvenWhenOffline() {

        Device offline = device("OFFLINE");
        Device stale = device("STALE");
        Device sensorFault = device("SENSOR_FAULT");

        assertEquals(
                "ALARM",
                service.riskLevel(offline, record("ALARM"), true),
                "活动告警 + OFFLINE 应保持 ALARM"
        );

        assertEquals(
                "ALARM",
                service.riskLevel(stale, record("WARNING"), true),
                "活动告警 + STALE 应保持 ALARM"
        );

        assertEquals(
                "ALARM",
                service.riskLevel(sensorFault, null, true),
                "活动告警 + SENSOR_FAULT 应保持 ALARM"
        );
    }

    /**
     * 无活动告警时走原有推导，不受影响。
     */
    @Test
    void noActiveAlarmFallsThroughToHealth() {

        Device offline = device("OFFLINE");

        assertEquals(
                "OFFLINE",
                service.riskLevel(offline, record("ALARM"), false)
        );
    }

    /**
     * NORMAL 不进入"当前需要处理"队列，
     * 其余异常级别全部进入。
     */
    @Test
    void onlyAbnormalLevelsEnterPendingQueue() {

        assertTrue(service.needsAttention("ALARM"));
        assertTrue(service.needsAttention("WARNING"));
        assertTrue(service.needsAttention("SENSOR_FAULT"));
        assertTrue(service.needsAttention("OFFLINE"));
        assertTrue(service.needsAttention("STALE"));
        assertTrue(service.needsAttention("DISPOSAL_PENDING"));

        assertFalse(
                service.needsAttention("NORMAL"),
                "NORMAL 不得进入紧急队列"
        );

        assertFalse(
                service.needsAttention(null),
                "空级别不得进入紧急队列"
        );
    }

    /* ==================================================
       DISPOSAL_PENDING（待完成处置）
       ================================================== */

    /**
     * 环境已恢复 + 存在未关闭的人工处置事件 → DISPOSAL_PENDING。
     */
    @Test
    void recoveredButDisposalOpenIsDisposalPending() {

        Device healthy = device("NORMAL");

        assertEquals(
                "DISPOSAL_PENDING",
                service.riskLevel(healthy, record("NORMAL"), false, true)
        );
    }

    /**
     * 活动告警仍在（环境未恢复）时，
     * 即使处置未关闭，仍保持 ALARM 最高优先级。
     */
    @Test
    void activeAlarmOutranksDisposalPending() {

        Device healthy = device("NORMAL");

        assertEquals(
                "ALARM",
                service.riskLevel(healthy, record("ALARM"), true, true)
        );
    }

    /**
     * DISPOSAL_PENDING 基础分低于 ALARM/WARNING/OFFLINE/STALE，
     * 但高于 NORMAL(0)。
     */
    @Test
    void disposalPendingBetweenAlarmAndNormal() {

        double pending =
                service.priorityScore("DISPOSAL_PENDING", 0.0, 0, 0, 0);
        double alarm =
                service.priorityScore("ALARM", 0.0, 0, 0, 0);
        double normal =
                service.priorityScore("NORMAL", 0.0, 0, 0, 0);

        assertEquals(15.0, pending, 0.001);
        assertTrue(
                pending < alarm,
                "待完成处置应低于正在告警的事件"
        );
        assertTrue(
                pending > normal,
                "待完成处置应高于 NORMAL，避免事件从视野消失"
        );
    }

    /* ==================================================
       riskScore 只参与 ALARM/WARNING 排序
       ================================================== */

    /**
     * OFFLINE / STALE / SENSOR_FAULT / DISPOSAL_PENDING
     * 的紧急度由基础分决定，禁止使用旧烟雾 riskScore 抬高：
     * 高风险分与低风险分的评分必须完全一致。
     */
    @Test
    void offlineScoreIgnoresOldRiskScore() {

        double highRisk = service.priorityScore("OFFLINE", 95.0, 0, 0, 0);
        double lowRisk = service.priorityScore("OFFLINE", 5.0, 0, 0, 0);

        assertEquals(25.0, highRisk, 0.001);
        assertEquals(highRisk, lowRisk, 0.001,
                "OFFLINE 不得用旧烟雾风险分抬高紧急度");
    }

    @Test
    void staleScoreIgnoresOldRiskScore() {

        double highRisk = service.priorityScore("STALE", 95.0, 0, 0, 0);
        double lowRisk = service.priorityScore("STALE", 5.0, 0, 0, 0);

        assertEquals(20.0, highRisk, 0.001);
        assertEquals(highRisk, lowRisk, 0.001,
                "STALE 不得用旧烟雾风险分抬高紧急度");
    }

    @Test
    void sensorFaultScoreIgnoresOldRiskScore() {

        double highRisk = service.priorityScore("SENSOR_FAULT", 95.0, 0, 0, 0);
        double lowRisk = service.priorityScore("SENSOR_FAULT", 5.0, 0, 0, 0);

        assertEquals(30.0, highRisk, 0.001);
        assertEquals(highRisk, lowRisk, 0.001,
                "SENSOR_FAULT 不得用旧烟雾风险分抬高紧急度");
    }

    @Test
    void disposalPendingScoreIgnoresOldRiskScore() {

        double highRisk =
                service.priorityScore("DISPOSAL_PENDING", 95.0, 0, 0, 0);
        double lowRisk =
                service.priorityScore("DISPOSAL_PENDING", 5.0, 0, 0, 0);

        assertEquals(15.0, highRisk, 0.001);
        assertEquals(highRisk, lowRisk, 0.001,
                "DISPOSAL_PENDING 不得用旧烟雾风险分抬高紧急度");
    }

    /**
     * 只有 ALARM / WARNING 才参与 riskScore 排序。
     */
    @Test
    void alarmAndWarningStillUseRiskScore() {

        double alarmHigh = service.priorityScore("ALARM", 90.0, 0, 0, 0);
        double alarmLow = service.priorityScore("ALARM", 10.0, 0, 0, 0);
        assertTrue(alarmHigh > alarmLow,
                "ALARM 应继续用 riskScore 排序");

        double warnHigh = service.priorityScore("WARNING", 90.0, 0, 0, 0);
        double warnLow = service.priorityScore("WARNING", 10.0, 0, 0, 0);
        assertTrue(warnHigh > warnLow,
                "WARNING 应继续用 riskScore 排序");
    }

    /**
     * 设备故障类（如 OFFLINE）即使带极高旧风险分，
     * 也不得超过正在 ALARM 的低风险事件。
     */
    @Test
    void staleWithHighOldRiskStillBelowActiveAlarm() {

        double staleHigh =
                service.priorityScore("STALE", 100.0, 0, 0, 0);
        double alarmLow =
                service.priorityScore("ALARM", 0.0, 0, 0, 0);

        assertTrue(
                alarmLow > staleHigh,
                "正在报警的事件必须压过带旧风险分的 STALE"
        );
    }

    private Device device(String healthStatus) {

        Device device = new Device();
        device.setDeviceId(101L);
        device.setHealthStatus(healthStatus);
        return device;
    }

    private SmokeRecord record(String cloudState) {

        SmokeRecord record = new SmokeRecord();
        record.setCloudState(cloudState);
        return record;
    }
}
