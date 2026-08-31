package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEMO 模拟设备场景生成测试。
 *
 * 校验：
 * - 场景切换 / 非法场景拒绝
 * - 各场景的输出是否符合预期（浓度 / ratio / 状态）
 * - 离线 / 过期场景停止上报
 * - 短时尖峰结束后自动回到 NORMAL
 * - 持续上升逐步触发预警
 */
public class DemoDeviceServiceTest {

    private final DemoDeviceService service =
            new DemoDeviceService();

    private long nextId() {
        return System.nanoTime() % 100000 + 10000;
    }

    @Test
    void unsupportedScenarioRejected() {

        long id = nextId();

        assertThrows(
                ResponseStatusException.class,
                () -> service.setScenario(id, "BOGUS")
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.setScenario(id, null)
        );
    }

    @Test
    void defaultScenarioIsNormal() {

        long id = nextId();

        assertEquals(
                "NORMAL",
                service.getScenario(id)
        );

        DemoOutput out = service.nextSample(id);

        assertEquals("NORMAL", out.edgeState());
        assertTrue(out.edgeRatio() < 1.5);
    }

    @Test
    void recoveryReturnsToNormalOverTime() {

        long id = nextId();

        service.setScenario(id, "RECOVERY");

        String lastState = "";
        double lastRatio = 3.0;

        for (int i = 0; i < 20; i++) {

            DemoOutput out = service.nextSample(id);
            lastState = out.edgeState();
            lastRatio = out.edgeRatio();
        }

        assertEquals(
                "NORMAL",
                lastState,
                "恢复结束后状态应回到 NORMAL"
        );

        assertTrue(
                lastRatio < 1.3,
                "恢复后 ratio 应回到正常，实际=" + lastRatio
        );
    }

    @Test
    void shortSpikeRisesThenFallsAndAutoReturnsToNormal() {

        long id = nextId();

        service.setScenario(id, "SHORT_SPIKE");

        double peakRatio = 1.0;

        for (int i = 0; i < 11; i++) {

            DemoOutput out = service.nextSample(id);
            peakRatio = Math.max(peakRatio, out.edgeRatio());
        }

        assertTrue(
                peakRatio > 1.5,
                "尖峰应明显升高，实际峰值ratio=" + peakRatio
        );

        assertEquals(
                "NORMAL",
                service.getScenario(id),
                "尖峰结束后应自动回到 NORMAL 场景"
        );

        DemoOutput tail = service.nextSample(id);
        assertEquals("NORMAL", tail.edgeState());
    }

    @Test
    void risingGraduallyReachesWarningAndBeyond() {

        long id = nextId();

        service.setScenario(id, "RISING");

        DemoOutput first = service.nextSample(id);

        assertTrue(
                first.edgeRatio() < 1.5,
                "持续上升起始应接近正常，实际=" + first.edgeRatio()
        );

        for (int i = 0; i < 8; i++) {
            service.nextSample(id);
        }

        DemoOutput later = service.nextSample(id);

        assertTrue(
                later.edgeRatio() >= 1.5,
                "上升后应达到预警及以上，实际=" + later.edgeRatio()
        );

        assertTrue(
                later.edgeState().equals("ALARM")
                        || later.edgeState().equals("PREWARNING"),
                "边缘状态应为 PREWARNING/ALARM"
        );
    }

    @Test
    void alarmScenarioProducesHighSmoke() {

        long id = nextId();

        service.setScenario(id, "ALARM");

        DemoOutput out = service.nextSample(id);

        double baseline = service.getBaseline(id);

        assertTrue(
                out.smokeValue() > baseline * 2.0,
                "ALARM 浓度应超过 2 倍基线"
        );

        assertEquals("ALARM", out.edgeState());
    }

    @Test
    void offlineAndStaleStopReporting() {

        long offlineId = nextId();
        service.setScenario(offlineId, "OFFLINE");
        assertNull(
                service.nextSample(offlineId),
                "OFFLINE 不应上报数据"
        );

        long staleId = nextId();
        service.setScenario(staleId, "STALE");
        assertNull(
                service.nextSample(staleId),
                "STALE 不应上报数据"
        );
    }

    @Test
    void switchingBackFromStaleResumesReporting() {

        long id = nextId();

        service.setScenario(id, "STALE");
        assertNull(service.nextSample(id));

        service.setScenario(id, "NORMAL");

        DemoOutput out = service.nextSample(id);

        assertEquals("NORMAL", out.edgeState());
        assertTrue(out.smokeValue() > 0);
    }
}
