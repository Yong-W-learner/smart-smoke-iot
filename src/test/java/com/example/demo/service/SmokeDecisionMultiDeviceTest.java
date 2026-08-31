package com.example.demo.service;

import com.example.demo.vo.SmokeDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 多设备判定状态隔离测试。
 *
 * 验证 1001（1栋1层101室，真实设备/REAL）与 1083（3栋5层503室，DEMO寝室）
 * 的云端基线、滑动窗口、报警锁存与恢复状态互不影响。
 */
public class SmokeDecisionMultiDeviceTest {

    /**
     * baseline 隔离：
     * 1001 基线约 20，1083 基线约 100。
     * 1001 尖峰 60（相对20=3倍）→ WARNING；
     * 1083 喂正常值 100（相对100=1倍）→ NORMAL。
     */
    @Test
    void baselineIsolatedBetweenDevices() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        // 1001 建立正常背景 ~20
        for (int i = 0; i < 6; i++) {
            service.analyze(1001L, 20);
        }

        // 1083 建立正常背景 ~100
        for (int i = 0; i < 6; i++) {
            service.analyze(1083L, 100);
        }

        // 1001 单点尖峰 60（3倍于自身基线）
        SmokeDecision spike1001 =
                service.analyze(1001L, 60);

        assertEquals(
                "WARNING",
                spike1001.getState()
        );

        // 1083 正常值 100（1倍于自身基线），不受1001影响
        SmokeDecision normal1083 =
                service.analyze(1083L, 100);

        assertEquals(
                "NORMAL",
                normal1083.getState()
        );
    }

    /**
     * 滑窗隔离：
     * 1083 持续上升触发 ALARM 时，
     * 1001 的正常序列保持 NORMAL。
     */
    @Test
    void slidingWindowIsolatedBetweenDevices() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        // 1001 正常背景
        for (int i = 0; i < 6; i++) {
            service.analyze(1001L, 20);
        }

        // 1083 正常背景 + 连续高浓度触发 ALARM
        service.analyze(1083L, 20);
        service.analyze(1083L, 20);
        service.analyze(1083L, 20);
        service.analyze(1083L, 20);
        service.analyze(1083L, 60);
        service.analyze(1083L, 60);

        SmokeDecision alarm1083 =
                service.analyze(1083L, 60);

        assertEquals(
                "ALARM",
                alarm1083.getState()
        );

        // 1001 不受 1083 滑窗/报警影响
        SmokeDecision normal1001 =
                service.analyze(1001L, 20);

        assertEquals(
                "NORMAL",
                normal1001.getState()
        );
    }

    /**
     * 报警锁存隔离：
     * 1083 进入 ALARM 并持续锁存时，
     * 1001 持续 NORMAL，不受 1083 锁存影响。
     */
    @Test
    void alarmLockIsolatedBetweenDevices() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        // 1001 正常背景
        for (int i = 0; i < 6; i++) {
            service.analyze(1001L, 20);
        }

        // 1083 正常背景 + 触发 ALARM
        for (int i = 0; i < 4; i++) {
            service.analyze(1083L, 20);
        }
        service.analyze(1083L, 60);
        service.analyze(1083L, 60);

        SmokeDecision alarm1083 =
                service.analyze(1083L, 60);

        assertEquals(
                "ALARM",
                alarm1083.getState()
        );

        // 1083 持续高浓度，保持 ALARM 锁存
        assertEquals(
                "ALARM",
                service.analyze(1083L, 60).getState()
        );

        // 1001 持续正常
        assertEquals(
                "NORMAL",
                service.analyze(1001L, 20).getState()
        );
    }

    /**
     * 恢复隔离：
     * 1083 从 ALARM 连续恢复3次解除报警的过程中，
     * 1001 的恢复计数不受影响，始终 NORMAL。
     */
    @Test
    void recoveryIsolatedBetweenDevices() {

        SmokeDecisionService service =
                new SmokeDecisionService();

        // 1001 正常背景
        for (int i = 0; i < 6; i++) {
            service.analyze(1001L, 20);
        }

        // 1083 触发 ALARM
        for (int i = 0; i < 4; i++) {
            service.analyze(1083L, 20);
        }
        service.analyze(1083L, 60);
        service.analyze(1083L, 60);

        assertEquals(
                "ALARM",
                service.analyze(1083L, 60).getState()
        );

        // 1083 第一次恢复：仍 ALARM
        assertEquals(
                "ALARM",
                service.analyze(1083L, 20).getState()
        );

        // 1083 第二次恢复：仍 ALARM
        assertEquals(
                "ALARM",
                service.analyze(1083L, 20).getState()
        );

        // 1083 第三次恢复：NORMAL
        assertEquals(
                "NORMAL",
                service.analyze(1083L, 20).getState()
        );

        // 1001 全程正常，不受 1083 恢复计数影响
        assertEquals(
                "NORMAL",
                service.analyze(1001L, 20).getState()
        );
    }
}
