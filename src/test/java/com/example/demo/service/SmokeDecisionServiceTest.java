package com.example.demo.service;

import com.example.demo.vo.SmokeDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmokeDecisionServiceTest {

    /**
     * 场景1：正常环境
     *
     * 20 → 20 → 21 → 20 → 20
     *
     * 期望：
     * NORMAL
     */
    @Test
    void testNormalEnvironment() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        service.analyze(20);
        service.analyze(20);
        service.analyze(21);
        service.analyze(20);

        SmokeDecision result =
                service.analyze(20);

        System.out.println(
                "正常环境：" + result
        );

        assertEquals(
                "NORMAL",
                result.getState()
        );
    }


    /**
     * 场景2：单点烟雾尖峰
     *
     * 20 → 20 → 20 → 20 → 60
     *
     * 普通固定阈值：
     * 60 > 50
     * 可能直接报警。
     *
     * 当前算法：
     * 只有一个高浓度异常点，
     * 因此只进入 WARNING。
     *
     * 这是误报抑制的核心测试之一。
     */
    @Test
    void testSingleSpike() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        service.analyze(20);
        service.analyze(20);
        service.analyze(20);
        service.analyze(20);

        SmokeDecision result =
                service.analyze(60);

        System.out.println(
                "瞬时尖峰：" + result
        );

        assertEquals(
                "WARNING",
                result.getState()
        );

        assertTrue(
                result.getRiskScore() > 0
        );
    }


    /**
     * 场景3：持续烟雾升高
     *
     * 20 → 25 → 35 → 50 → 65
     *
     * 后三个点已经连续处于
     * 云端异常区间，
     * 且当前值明显高于背景基线。
     *
     * 期望：
     * ALARM
     */
    @Test
    void testContinuousSmokeRise() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        service.analyze(20);
        service.analyze(25);
        service.analyze(35);
        service.analyze(50);

        SmokeDecision result =
                service.analyze(65);

        System.out.println(
                "持续升高：" + result
        );

        assertEquals(
                "ALARM",
                result.getState()
        );

        assertTrue(
                result.getRiskScore() >= 50
        );
    }


    /**
     * 场景4：具有明显持续上升趋势，
     * 但尚未满足正式报警条件
     *
     * 20 → 22 → 25 → 29 → 34
     *
     * 连续上升明显，
     * 但是浓度尚未形成持续高位。
     *
     * 期望：
     * WARNING
     */
    @Test
    void testRisingTrend() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        service.analyze(20);
        service.analyze(22);
        service.analyze(25);
        service.analyze(29);

        SmokeDecision result =
                service.analyze(34);

        System.out.println(
                "持续上升趋势：" + result
        );

        assertEquals(
                "WARNING",
                result.getState()
        );
    }


    /**
     * 场景5：持续高浓度不能被
     * 重新当成正常环境
     *
     * 先建立正常背景：
     *
     * 20 → 20 → 20 → 20 → 20
     *
     * 随后持续高浓度：
     *
     * 60 → 60 → 60 → 60 → 60 → 60
     *
     * 第一版算法会因为滑动窗口
     * 最终全部变为60而错误恢复 NORMAL。
     *
     * 新算法具有稳定背景基线和
     * ALARM状态锁存，
     * 因此必须持续保持 ALARM。
     */
    @Test
    void testSustainedHighSmokeShouldRemainAlarm() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        // 建立正常背景
        service.analyze(20);
        service.analyze(20);
        service.analyze(20);
        service.analyze(20);
        service.analyze(20);

        // 第一个高值：WARNING
        SmokeDecision r1 =
                service.analyze(60);

        // 第二个高值：WARNING
        SmokeDecision r2 =
                service.analyze(60);

        // 第三个连续高值：ALARM
        SmokeDecision r3 =
                service.analyze(60);

        // 后续继续高浓度
        SmokeDecision r4 =
                service.analyze(60);

        SmokeDecision r5 =
                service.analyze(60);

        SmokeDecision r6 =
                service.analyze(60);

        System.out.println(
                "持续高浓度第一次：" + r1
        );

        System.out.println(
                "持续高浓度第二次：" + r2
        );

        System.out.println(
                "进入报警：" + r3
        );

        System.out.println(
                "持续报警：" + r6
        );

        assertEquals(
                "WARNING",
                r1.getState()
        );

        assertEquals(
                "WARNING",
                r2.getState()
        );

        assertEquals(
                "ALARM",
                r3.getState()
        );

        assertEquals(
                "ALARM",
                r4.getState()
        );

        assertEquals(
                "ALARM",
                r5.getState()
        );

        assertEquals(
                "ALARM",
                r6.getState()
        );
    }


    /**
     * 场景6：报警后不能因为
     * 一次正常数据立即解除报警
     *
     * 先触发：
     *
     * 20 → 20 → 20 → 60 → 60 → 60
     *
     * 进入 ALARM。
     *
     * 然后：
     *
     * 20 → 第1次恢复
     * 20 → 第2次恢复
     * 20 → 第3次恢复
     *
     * 必须连续3次正常以后
     * 才真正恢复 NORMAL。
     */
    @Test
    void testAlarmRecoveryNeedsThreeNormalSamples() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        // 正常背景
        service.analyze(20);
        service.analyze(20);
        service.analyze(20);

        // 持续异常
        service.analyze(60);
        service.analyze(60);

        SmokeDecision alarm =
                service.analyze(60);

        assertEquals(
                "ALARM",
                alarm.getState()
        );


        // 第一次恢复
        SmokeDecision recover1 =
                service.analyze(20);

        System.out.println(
                "恢复1：" + recover1
        );

        assertEquals(
                "ALARM",
                recover1.getState()
        );


        // 第二次恢复
        SmokeDecision recover2 =
                service.analyze(20);

        System.out.println(
                "恢复2：" + recover2
        );

        assertEquals(
                "ALARM",
                recover2.getState()
        );


        // 第三次恢复
        SmokeDecision recover3 =
                service.analyze(20);

        System.out.println(
                "恢复3：" + recover3
        );

        assertEquals(
                "NORMAL",
                recover3.getState()
        );
    }
}