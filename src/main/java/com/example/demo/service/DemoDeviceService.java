package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEMO 模拟寝室设备的数据生成器。
 *
 * 每个 DEMO 设备独立运行一个场景：
 *
 * NORMAL         正常环境
 * WARNING        预警（浓度渐升）
 * ALARM          持续高浓度报警
 * RECOVERY       从高浓度逐步恢复
 * SHORT_SPIKE    短时尖峰（快速升高后回落，自动回到 NORMAL）
 * RISING         持续上升（逐步触发 WARNING → ALARM）
 * OFFLINE        设备离线（不上报数据）
 * STALE          数据过期（不上报，标记为 STALE）
 * SENSOR_FAULT   传感器数据异常
 *
 * 生成的数据由 DemoDeviceTask 送入
 * 与真实设备完全相同的 SmokeReportService 链路。
 */
@Service
public class DemoDeviceService {

    public static final String SCENARIO_NORMAL = "NORMAL";
    public static final String SCENARIO_WARNING = "WARNING";
    public static final String SCENARIO_ALARM = "ALARM";
    public static final String SCENARIO_RECOVERY = "RECOVERY";
    public static final String SCENARIO_SHORT_SPIKE = "SHORT_SPIKE";
    public static final String SCENARIO_RISING = "RISING";
    public static final String SCENARIO_OFFLINE = "OFFLINE";
    public static final String SCENARIO_STALE = "STALE";
    public static final String SCENARIO_SENSOR_FAULT = "SENSOR_FAULT";

    private static final Set<String> SCENARIOS = Set.of(
            SCENARIO_NORMAL,
            SCENARIO_WARNING,
            SCENARIO_ALARM,
            SCENARIO_RECOVERY,
            SCENARIO_SHORT_SPIKE,
            SCENARIO_RISING,
            SCENARIO_OFFLINE,
            SCENARIO_STALE,
            SCENARIO_SENSOR_FAULT
    );

    /**
     * 可用的场景集合（供演示控制页展示）。
     */
    public static Set<String> getScenarios() {
        return SCENARIOS;
    }

    /**
     * RECOVERY 场景总步数（约12次采样内从2.4倍回到1倍）
     */
    private static final int RECOVERY_STEPS = 12;

    /**
     * SHORT_SPIKE 场景总步数（约10次采样内完成一次尖峰）
     */
    private static final int SPIKE_STEPS = 10;

    /**
     * RISING 场景每一步浓度上升幅度
     */
    private static final double RISING_STEP_RATIO = 0.15;

    /**
     * 每台 DEMO 设备的场景状态
     */
    private final Map<Long, DemoState> states =
            new ConcurrentHashMap<>();

    private final Random random = new Random();


    /**
     * 切换某台 DEMO 设备的场景，并重置步数。
     */
    public void setScenario(Long deviceId, String scenario) {

        if (scenario == null
                || !SCENARIOS.contains(scenario.toUpperCase())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不支持的场景：" + scenario
            );
        }

        DemoState state =
                states.computeIfAbsent(
                        deviceId,
                        DemoState::new
                );

        state.setScenario(
                scenario.toUpperCase()
        );

        state.setStep(0);
    }


    /**
     * 当前场景（默认 NORMAL）。
     */
    public String getScenario(Long deviceId) {

        return states.computeIfAbsent(
                        deviceId,
                        DemoState::new
                )
                .getScenario();
    }


    /**
     * DEMO 设备当前的边缘端基线（默认 20）。
     */
    public double getBaseline(Long deviceId) {

        return states.computeIfAbsent(
                        deviceId,
                        DemoState::new
                )
                .getBaseline();
    }


    /**
     * 生成该设备下一次上报的数据。
     *
     * @return DemoOutput 表示一条有效上报；
     *         null 表示设备离线（OFLLINE 场景，不上报）。
     */
    public DemoOutput nextSample(Long deviceId) {

        DemoState state =
                states.computeIfAbsent(
                        deviceId,
                        DemoState::new
                );

        String scenario = state.getScenario();
        double baseline = state.getBaseline();
        int step = state.getStep();

        state.setStep(step + 1);

        switch (scenario) {

            case SCENARIO_OFFLINE:
                return null;

            case SCENARIO_STALE:
                /*
                 * 数据过期：设备不再上报。
                 * DemoDeviceTask 会据此标记 STALE（而非 OFFLINE）。
                 */
                return null;

            case SCENARIO_SHORT_SPIKE:
                /*
                 * 短时尖峰：以正弦波形快速升高到峰值(~3.2倍)
                 * 再快速回落；结束后自动回到 NORMAL 场景。
                 */
                if (step >= SPIKE_STEPS) {

                    state.setScenario(SCENARIO_NORMAL);

                    return normalSample(state);
                }

                double spikePos =
                        Math.sin(
                                step
                                        / (double) SPIKE_STEPS
                                        * Math.PI
                        );

                double spikeRatio =
                        1.0 + 2.2 * spikePos;

                double spikeSmoke =
                        max0(
                                baseline * spikeRatio
                                        + randomNoise(baseline * 0.05)
                        );

                double spikeComputedRatio =
                        spikeSmoke / baseline;

                return new DemoOutput(
                        spikeSmoke,
                        baseline,
                        spikeComputedRatio,
                        edgeStateFor(spikeComputedRatio)
                );

            case SCENARIO_RISING:
                /*
                 * 持续上升：浓度不断升高直至封顶 ~3.5 倍，
                 * 完整触发 WARNING → ALARM 链路。
                 */
                double risingRatio =
                        Math.min(
                                3.5,
                                1.0 + step * RISING_STEP_RATIO
                        );

                double risingSmoke =
                        max0(
                                baseline * risingRatio
                                        + randomNoise(baseline * 0.05)
                        );

                double risingComputedRatio =
                        risingSmoke / baseline;

                return new DemoOutput(
                        risingSmoke,
                        baseline,
                        risingComputedRatio,
                        edgeStateFor(risingComputedRatio)
                );

            case SCENARIO_SENSOR_FAULT:
                /*
                 * 模拟 sensor 数据异常：
                 * Smoke_Ratio 与 Smoke_Value/Baseline 不一致，
                 * Smoke_State 为空。
                 */
                return new DemoOutput(
                        baseline,
                        baseline,
                        8.0,
                        null
                );

            case SCENARIO_WARNING:
                /*
                 * 预警：浓度从 1.4 倍逐渐升到 1.75 倍
                 */
                double warnRatio =
                        1.4 + Math.min(
                                0.35,
                                step * 0.03
                        );

                double warnSmoke =
                        max0(
                                baseline * warnRatio
                                        + randomNoise(baseline * 0.05)
                        );

                return new DemoOutput(
                        warnSmoke,
                        baseline,
                        warnSmoke / baseline,
                        "PREWARNING"
                );

            case SCENARIO_ALARM:
                /*
                 * 持续高浓度：2.6 ~ 3.4 倍
                 */
                double alarmRatio =
                        2.6 + random.nextDouble() * 0.8;

                double alarmSmoke =
                        max0(
                                baseline * alarmRatio
                                        + randomNoise(baseline * 0.05)
                        );

                return new DemoOutput(
                        alarmSmoke,
                        baseline,
                        alarmSmoke / baseline,
                        "ALARM"
                );

            case SCENARIO_RECOVERY:
                /*
                 * 恢复：从 2.4 倍逐步回到 0.95 倍
                 */
                double progress =
                        Math.min(
                                1.0,
                                step / (double) RECOVERY_STEPS
                        );

                double recoverSmoke =
                        baseline
                                * (2.4 * (1 - progress)
                                + 0.95 * progress)
                                + randomNoise(baseline * 0.06);

                recoverSmoke = max0(recoverSmoke);

                double recoverRatio =
                        recoverSmoke / baseline;

                String edgeState =
                        recoverRatio >= 2.0
                                ? "ALARM"
                                : (recoverRatio >= 1.5
                                ? "PREWARNING"
                                : "NORMAL");

                return new DemoOutput(
                        recoverSmoke,
                        baseline,
                        recoverRatio,
                        edgeState
                );

            case SCENARIO_NORMAL:
            default:
                return normalSample(state);
        }
    }


    /**
     * NORMAL 环境采样：0.92 ~ 1.08 倍小幅波动。
     */
    private DemoOutput normalSample(DemoState state) {

        double baseline = state.getBaseline();

        double normalRatio =
                0.92 + random.nextDouble() * 0.16;

        double normalSmoke =
                max0(baseline * normalRatio);

        return new DemoOutput(
                normalSmoke,
                baseline,
                normalSmoke / baseline,
                "NORMAL"
        );
    }


    /**
     * 由 Smoke_Ratio 推导边缘端判定状态。
     */
    private String edgeStateFor(double ratio) {

        return ratio >= 2.0
                ? "ALARM"
                : (ratio >= 1.5
                ? "PREWARNING"
                : "NORMAL");
    }


    private double randomNoise(double range) {

        return (random.nextDouble() - 0.5) * range;
    }


    private double max0(double value) {

        return Math.max(0.1, value);
    }


    /**
     * 单台 DEMO 设备的场景状态。
     */
    private static class DemoState {

        private final Long deviceId;

        private String scenario = SCENARIO_NORMAL;

        private int step = 0;

        private double baseline = 20.0;

        DemoState(Long deviceId) {
            this.deviceId = deviceId;
        }

        public String getScenario() {
            return scenario;
        }

        public void setScenario(String scenario) {
            this.scenario = scenario;
        }

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }

        public double getBaseline() {
            return baseline;
        }

        @SuppressWarnings("unused")
        public Long getDeviceId() {
            return deviceId;
        }
    }
}
