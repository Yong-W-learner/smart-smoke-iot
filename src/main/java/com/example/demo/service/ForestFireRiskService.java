package com.example.demo.service;

import org.springframework.stereotype.Service;

/**
 * 火险可信度评分（0~100）。
 *
 * 透明规则，非AI：
 * 火险可信度 = 烟雾异常证据分 × 0.70
 *            + 云端判定 ALARM × 15
 *            + 边缘判定 ALARM × 5
 *            + 无人机确认火点 × 20
 *
 * 其中"烟雾异常证据分"即 SmokeDecisionService 的 riskScore
 * （滑动窗口 / 动态基线 / 连续高浓度判定），
 * 算法与阈值完全复用既有实现，不做任何修改。
 */
@Service
public class ForestFireRiskService {

    /** 烟雾异常证据分权重 */
    public static final double SMOKE_WEIGHT = 0.70;

    /** 云端判定 ALARM 加分 */
    public static final int CLOUD_ALARM_BONUS = 15;

    /** 边缘判定 ALARM 加分 */
    public static final int EDGE_ALARM_BONUS = 5;

    /** 无人机确认火点加分 */
    public static final int DRONE_CONFIRMED_BONUS = 20;

    /**
     * 计算火险可信度（0~100）。
     *
     * @param smokeRiskScore 烟雾异常证据分（SmokeDecision.riskScore，可空）
     * @param cloudState     云端判定状态（NORMAL / WARNING / ALARM）
     * @param edgeState      边缘端状态（NORMAL / PREWARNING / ALARM，可空）
     * @param droneConfirmed 无人机是否已确认火点
     */
    public int fireConfidenceScore(Double smokeRiskScore,
                                   String cloudState,
                                   String edgeState,
                                   boolean droneConfirmed) {

        double smoke = (smokeRiskScore == null
                ? 0
                : clamp(smokeRiskScore, 0, 100)) * SMOKE_WEIGHT;

        double cloud = "ALARM".equals(cloudState)
                ? CLOUD_ALARM_BONUS
                : 0;

        double edge = "ALARM".equals(edgeState)
                ? EDGE_ALARM_BONUS
                : 0;

        double drone = droneConfirmed
                ? DRONE_CONFIRMED_BONUS
                : 0;

        return (int) Math.round(
                clamp(smoke + cloud + edge + drone, 0, 100)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
