package com.example.demo.service;

import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import org.springframework.stereotype.Service;

/**
 * 宿舍工作台的"寝室风险级别"推导与"紧急事件优先级"评分。
 *
 * 纯函数设计，便于单元测试。
 */
@Service
public class EventPriorityService {

    /** 待完成处置：环境已恢复，但人工处置流程尚未关闭。 */
    public static final String RISK_DISPOSAL_PENDING = "DISPOSAL_PENDING";

    /**
     * 由 设备健康状态 + 最新云端判定 推导展示用风险级别。
     *
     * 等价于无活动告警、无待完成处置时调用
     * {@link #riskLevel(Device, SmokeRecord, boolean, boolean)}。
     */
    public String riskLevel(
            Device device,
            SmokeRecord latest) {

        return riskLevel(
                device,
                latest,
                false,
                false
        );
    }


    /**
     * 由 设备健康状态 + 最新云端判定 + 是否存在活动告警
     * 推导展示用风险级别（无待完成处置）。
     *
     * 等价于
     * {@link #riskLevel(Device, SmokeRecord, boolean, boolean)}。
     */
    public String riskLevel(
            Device device,
            SmokeRecord latest,
            boolean hasActiveAlarm) {

        return riskLevel(
                device,
                latest,
                hasActiveAlarm,
                false
        );
    }


    /**
     * 由 设备健康状态 + 最新云端判定 + 是否存在活动告警
     * + 是否存在待完成处置事件，推导展示用风险级别。
     *
     * 严重度降序：
     * ALARM > WARNING > SENSOR_FAULT > OFFLINE > STALE
     *      > DISPOSAL_PENDING > NORMAL
     *
     * 规则（按序优先）：
     * 1. 存在活动烟雾告警（recover_time IS NULL）→ 恒为 ALARM，
     *    即使设备随后 OFFLINE / STALE / SENSOR_FAULT，
     *    也必须保持最高 ALARM 优先级；
     *    设备健康状态作为附加信息单独展示。
     * 2. 否则 healthStatus = OFFLINE / STALE / SENSOR_FAULT
     * 3. 否则 cloudState = ALARM / WARNING
     * 4. 否则存在待完成处置事件（disposal_state != CLOSED）→
     *    DISPOSAL_PENDING（待完成处置），环境已恢复但人工流程未关闭；
     *    优先级高于 NORMAL、低于正在告警/预警的事件。
     * 5. 其余 NORMAL
     */
    public String riskLevel(
            Device device,
            SmokeRecord latest,
            boolean hasActiveAlarm,
            boolean hasOpenDisposal) {

        if (hasActiveAlarm) {
            return "ALARM";
        }

        String health =
                device == null
                        ? null
                        : device.getHealthStatus();

        if ("OFFLINE".equals(health)) {
            return "OFFLINE";
        }

        if ("STALE".equals(health)) {
            return "STALE";
        }

        if ("SENSOR_FAULT".equals(health)) {
            return "SENSOR_FAULT";
        }

        String cloud =
                latest == null
                        ? null
                        : latest.getCloudState();

        if ("ALARM".equals(cloud)) {
            return "ALARM";
        }

        if ("WARNING".equals(cloud)) {
            return "WARNING";
        }

        if (hasOpenDisposal) {
            return RISK_DISPOSAL_PENDING;
        }

        return "NORMAL";
    }


    /**
     * 该风险级别是否需要进入"当前需要处理"队列。
     *
     * NORMAL 不进入队列，其余全部进入。
     */
    public boolean needsAttention(String riskLevel) {

        return riskLevel != null
                && !"NORMAL".equals(riskLevel);
    }


    /**
     * 计算紧急事件优先级 0~100，越大越紧急。
     *
     * 公式：
     * 基础分（ALARM=60 / WARNING=40 / SENSOR_FAULT=30 /
     *         OFFLINE=25 / STALE=20 / DISPOSAL_PENDING=15 / NORMAL=0）
     * + 风险分 riskScore * 0.3（仅 ALARM / WARNING 参与排序；
     *   OFFLINE / STALE / SENSOR_FAULT / DISPOSAL_PENDING
     *   不得用旧烟雾风险分抬高紧急度，risk=0）
     * + 持续时间 activeDurationMinutes * 0.15（0~9，封顶60分钟）
     * + 未确认告警 +10（unacknowledged=1 时）
     * + 近24h重复告警 recentAnomalyCount * 2（0~10，封顶5次）
     *
     * DISPOSAL_PENDING 基础分 15 保证：
     * 待完成处置事件高于 NORMAL(0) 进入队列，
     * 但低于所有正在告警/预警的事件。
     *
     * 总分封顶 100。
     *
     * @param riskLevel            展示用风险级别
     * @param riskScore            最新记录风险分（可空）
     * @param activeDurationMinutes 活动告警已持续分钟数
     * @param unacknowledged       1 表示存在未确认的活动告警
     * @param recentAnomalyCount   近24h告警次数
     */
    public double priorityScore(
            String riskLevel,
            Double riskScore,
            long activeDurationMinutes,
            int unacknowledged,
            int recentAnomalyCount) {

        double base;

        switch (riskLevel == null
                ? "NORMAL"
                : riskLevel) {

            case "ALARM":
                base = 60;
                break;

            case "WARNING":
                base = 40;
                break;

            case "SENSOR_FAULT":
                base = 30;
                break;

            case "OFFLINE":
                base = 25;
                break;

            case "STALE":
                base = 20;
                break;

            case "DISPOSAL_PENDING":
                base = 15;
                break;

            default:
                base = 0;
                break;
        }

        /*
         * 旧烟雾风险分只参与 ALARM / WARNING 排序。
         *
         * OFFLINE / STALE / SENSOR_FAULT / DISPOSAL_PENDING
         * 的紧急度由基础分决定，禁止用陈旧 riskScore 抬高，
         * 避免"早已恢复的烟雾风险分"把设备故障 / 待闭环
         * 误判为比正在告警还紧急。
         */
        String level = riskLevel == null
                ? "NORMAL" : riskLevel;

        boolean riskEligible =
                "ALARM".equals(level)
                        || "WARNING".equals(level);

        double risk =
                riskEligible && riskScore != null
                        ? Math.min(
                        Math.max(riskScore, 0),
                        100
                ) * 0.3
                        : 0;

        double duration =
                Math.min(
                        Math.max(activeDurationMinutes, 0),
                        60
                ) * 0.15;

        double unack =
                unacknowledged == 0
                        ? 0
                        : 10;

        double recent =
                Math.min(
                        Math.max(recentAnomalyCount, 0),
                        5
                ) * 2;

        return Math.min(
                base + risk + duration + unack + recent,
                100
        );
    }
}
