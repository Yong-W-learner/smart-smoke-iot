package com.example.demo.service;

import com.example.demo.vo.SmokeDecision;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SmokeDecisionService {

    /**
     * 滑动窗口大小
     */
    private static final int WINDOW_SIZE = 5;

    /**
     * 云端预警倍率
     *
     * 注意：
     * 这是云端持续性分析参数，
     * 不是硬件端的报警阈值。
     */
    private static final double WARNING_RATIO = 1.5;

    /**
     * 云端正式报警倍率
     */
    private static final double ALARM_RATIO = 2.0;

    /**
     * 允许更新背景基线的最大倍率
     *
     * 只有当前烟雾仍非常接近正常环境时，
     * 才允许基线缓慢变化。
     */
    private static final double BASELINE_UPDATE_MAX_RATIO = 1.20;

    /**
     * 云端背景基线更新速度
     */
    private static final double BASELINE_ALPHA = 0.02;

    /**
     * ALARM 恢复判断倍率
     *
     * 必须回落到基线的 1.3 倍以内，
     * 才认为出现一次恢复采样。
     */
    private static final double RECOVERY_RATIO = 1.30;

    /**
     * 连续多少次恢复正常后，
     * 才真正解除 ALARM。
     */
    private static final int RECOVERY_COUNT_REQUIRED = 3;

    /**
     * 连续多少次处于异常高位，
     * 才允许进入正式 ALARM。
     */
    private static final int ALARM_HIGH_COUNT_REQUIRED = 3;

    /**
     * 每台设备的云端智能判定状态。
     *
     * key 是数据库 device_id。
     *
     * 每台设备独立保存：
     * 滑动窗口、背景基线、当前状态、恢复计数。
     *
     * 这样多台设备之间
     * 不会互相污染判定状态。
     */
    private final Map<Long, DeviceDecisionContext> contexts =
            new ConcurrentHashMap<>();


    /**
     * 输入某个设备的一条新烟雾浓度，
     * 返回该设备独立的云端智能判定结果。
     *
     * 设备之间互不影响。
     *
     * 并发说明：
     * 只对单台设备的上下文加锁，
     * 因此不同设备可以并行分析，
     * 同一设备内部仍保持串行安全。
     */
    public SmokeDecision analyze(
            Long deviceId,
            double smokeValue) {

        DeviceDecisionContext ctx =
                contexts.computeIfAbsent(
                        deviceId,
                        id -> new DeviceDecisionContext()
                );

        synchronized (ctx) {
            return analyze(ctx, smokeValue);
        }
    }


    /**
     * 基于单台设备的独立状态完成智能判定。
     */
    private SmokeDecision analyze(
            DeviceDecisionContext ctx,
            double smokeValue) {

        /*
         * ========================================
         * 1. 初始化云端背景基线
         * ========================================
         */
        if (ctx.getCloudBaseline() == null) {

            ctx.setCloudBaseline(
                    Math.max(smokeValue, 1.0)
            );

            addToWindow(ctx, smokeValue);

            return new SmokeDecision(
                    "NORMAL",
                    0,
                    "云端背景基线初始化中"
            );
        }


        /*
         * ========================================
         * 2. 加入滑动窗口
         * ========================================
         */
        addToWindow(ctx, smokeValue);

        double ratio =
                smokeValue / ctx.getCloudBaseline();


        /*
         * ========================================
         * 3. 如果当前已经处于 ALARM
         *
         * 不允许高浓度持续一段时间后
         * 自动被当成新的正常环境。
         *
         * 必须连续多次恢复到正常范围，
         * 才解除报警。
         * ========================================
         */
        if ("ALARM".equals(ctx.getCurrentState())) {

            if (ratio <= RECOVERY_RATIO) {

                ctx.setRecoveryCount(
                        ctx.getRecoveryCount() + 1
                );

                /*
                 * 已经连续多次恢复正常
                 */
                if (ctx.getRecoveryCount()
                        >= RECOVERY_COUNT_REQUIRED) {

                    ctx.setCurrentState("NORMAL");
                    ctx.setRecoveryCount(0);

                    /*
                     * 恢复以后可以重新允许
                     * 正常环境基线缓慢适应。
                     */
                    updateBaselineIfSafe(
                            ctx,
                            smokeValue,
                            ratio
                    );

                    return new SmokeDecision(
                            "NORMAL",
                            0,
                            "烟雾浓度已连续"
                                    + RECOVERY_COUNT_REQUIRED
                                    + "次恢复至正常范围，解除云端报警"
                    );
                }

                return new SmokeDecision(
                        "ALARM",
                        calculateRiskScore(
                                ratio,
                                0,
                                0
                        ),
                        "烟雾正在恢复，已连续"
                                + ctx.getRecoveryCount()
                                + "/"
                                + RECOVERY_COUNT_REQUIRED
                                + "次回到正常范围，暂保持报警"
                );

            } else {

                /*
                 * 又出现高值，恢复计数清零
                 */
                ctx.setRecoveryCount(0);

                return new SmokeDecision(
                        "ALARM",
                        calculateRiskScore(
                                ratio,
                                countConsecutiveHigh(ctx),
                                countRisingTrend(ctx)
                        ),
                        "烟雾仍处于高浓度状态，云端保持报警"
                );
            }
        }


        /*
         * ========================================
         * 4. 滑动窗口还没有形成
         * ========================================
         */
        if (ctx.getSmokeWindow().size()
                < WINDOW_SIZE) {

            /*
             * 只有正常小幅变化时才允许更新基线
             */
            updateBaselineIfSafe(
                    ctx,
                    smokeValue,
                    ratio
            );

            return new SmokeDecision(
                    "NORMAL",
                    0,
                    "云端滑动窗口初始化中"
            );
        }


        /*
         * ========================================
         * 5. 分析最近数据
         * ========================================
         */

        int consecutiveHigh =
                countConsecutiveHigh(ctx);

        int risingCount =
                countRisingTrend(ctx);

        double riskScore =
                calculateRiskScore(
                        ratio,
                        consecutiveHigh,
                        risingCount
                );


        /*
         * ========================================
         * 6. 正式报警判定
         *
         * 要求：
         *
         * 当前相对背景明显升高
         * +
         * 连续多次异常
         *
         * 避免单点尖峰直接成为正式火警。
         * ========================================
         */
        if (ratio >= ALARM_RATIO
                && consecutiveHigh
                >= ALARM_HIGH_COUNT_REQUIRED) {

            ctx.setCurrentState("ALARM");
            ctx.setRecoveryCount(0);

            return new SmokeDecision(
                    "ALARM",
                    riskScore,
                    "烟雾持续异常：连续"
                            + consecutiveHigh
                            + "次处于异常高位，当前约为云端背景基线的"
                            + String.format("%.2f", ratio)
                            + "倍"
            );
        }


        /*
         * ========================================
         * 7. 单点尖峰
         *
         * 当前很高，
         * 但是之前没有连续异常。
         *
         * 不立即作为正式火警。
         * ========================================
         */
        if (ratio >= ALARM_RATIO
                && consecutiveHigh == 1) {

            ctx.setCurrentState("WARNING");

            return new SmokeDecision(
                    "WARNING",
                    riskScore,
                    "检测到单点烟雾浓度突增，暂未达到持续报警条件"
            );
        }


        /*
         * ========================================
         * 8. 普通预警
         *
         * 相对浓度明显升高
         * 或者
         * 最近数据呈持续上升趋势
         * ========================================
         */
        if (ratio >= WARNING_RATIO
                || risingCount >= 3) {

            ctx.setCurrentState("WARNING");

            return new SmokeDecision(
                    "WARNING",
                    riskScore,
                    "检测到烟雾浓度升高或持续上升趋势"
            );
        }


        /*
         * ========================================
         * 9. NORMAL
         * ========================================
         */

        ctx.setCurrentState("NORMAL");

        updateBaselineIfSafe(
                ctx,
                smokeValue,
                ratio
        );

        return new SmokeDecision(
                "NORMAL",
                riskScore,
                "烟雾浓度及短期变化趋势正常"
        );
    }


    /**
     * 加入滑动窗口。
     */
    private void addToWindow(
            DeviceDecisionContext ctx,
            double value) {

        ctx.getSmokeWindow().addLast(value);

        if (ctx.getSmokeWindow().size() > WINDOW_SIZE) {
            ctx.getSmokeWindow().removeFirst();
        }
    }


    /**
     * 统计从当前时刻往前，
     * 连续多少个数据位于异常区间。
     */
    private int countConsecutiveHigh(
            DeviceDecisionContext ctx) {

        int count = 0;

        List<Double> values =
                new ArrayList<>(ctx.getSmokeWindow());

        for (int i = values.size() - 1;
             i >= 0;
             i--) {

            double ratio =
                    values.get(i)
                            / ctx.getCloudBaseline();

            if (ratio >= WARNING_RATIO) {
                count++;
            } else {
                break;
            }
        }

        return count;
    }


    /**
     * 判断最近窗口中明显上升了多少次。
     *
     * 相邻两个值至少增长5%，
     * 才算一次明显上升。
     */
    private int countRisingTrend(
            DeviceDecisionContext ctx) {

        int count = 0;

        List<Double> values =
                new ArrayList<>(ctx.getSmokeWindow());

        for (int i = 1;
             i < values.size();
             i++) {

            double previous =
                    values.get(i - 1);

            double current =
                    values.get(i);

            if (previous > 0
                    && current
                    > previous * 1.05) {

                count++;
            }
        }

        return count;
    }


    /**
     * 正常环境下缓慢更新云端背景基线。
     *
     * 和 BearPi 边缘端设计理念类似：
     *
     * 正常环境缓慢适应；
     * 出现明显异常后冻结基线。
     */
    private void updateBaselineIfSafe(
            DeviceDecisionContext ctx,
            double smokeValue,
            double ratio) {

        if ("NORMAL".equals(ctx.getCurrentState())
                && ratio
                <= BASELINE_UPDATE_MAX_RATIO) {

            ctx.setCloudBaseline(
                    ctx.getCloudBaseline()
                            * (1.0
                            - BASELINE_ALPHA)
                            + smokeValue
                            * BASELINE_ALPHA
            );
        }
    }


    /**
     * 计算风险评分 0~100。
     */
    private double calculateRiskScore(
            double ratio,
            int consecutiveHigh,
            int risingCount) {

        double riskScore = 0;

        /*
         * 相对浓度：最高贡献50分
         */
        riskScore += Math.min(
                Math.max(
                        (ratio - 1.0) * 30,
                        0
                ),
                50
        );

        /*
         * 连续异常：最高30分
         */
        riskScore += Math.min(
                consecutiveHigh * 10,
                30
        );

        /*
         * 上升趋势：最高20分
         */
        riskScore += Math.min(
                risingCount * 5,
                20
        );

        return Math.min(
                riskScore,
                100
        );
    }
}
