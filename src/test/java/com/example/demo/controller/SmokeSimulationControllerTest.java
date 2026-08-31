package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.service.DemoDeviceService;
import com.example.demo.service.SmokeReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟注入安全边界测试：
 *
 * 人工注入烟雾 / 场景切换只允许作用于 DEMO 模拟设备，
 * 真实硬件设备必须被拒绝，
 * 防止伪造数据污染真实设备的 smoke_record / 告警 / 健康状态。
 */
@ExtendWith(MockitoExtension.class)
public class SmokeSimulationControllerTest {

    @Mock
    private SmokeReportService smokeReportService;

    @Mock
    private DemoDeviceService demoDeviceService;

    @Mock
    private DeviceMapper deviceMapper;

    @InjectMocks
    private SmokeSimulationController controller;

    private Device realDevice() {
        Device device = new Device();
        device.setDeviceId(1001L);
        device.setSourceType("REAL");
        return device;
    }

    private Device demoDevice() {
        Device device = new Device();
        device.setDeviceId(1083L);
        device.setSourceType("DEMO");
        return device;
    }

    @Test
    void realDeviceRejectedFromSmokeInjection() {

        when(deviceMapper.selectById(1001L)).thenReturn(realDevice());

        assertThrows(
                ResponseStatusException.class,
                () -> controller.simulateSmoke(1001L, 60)
        );

        verify(smokeReportService, never())
                .persistSample(any(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void realDeviceRejectedFromScenarioSwitch() {

        when(deviceMapper.selectById(1001L)).thenReturn(realDevice());

        assertThrows(
                ResponseStatusException.class,
                () -> controller.scenario(1001L, "ALARM")
        );

        verify(demoDeviceService, never())
                .setScenario(any(), any());
    }

    @Test
    void demoDeviceAcceptsScenarioSwitch() {

        when(deviceMapper.selectById(1083L)).thenReturn(demoDevice());
        when(demoDeviceService.getScenario(1083L)).thenReturn("ALARM");

        Map<String, Object> result =
                controller.scenario(1083L, "ALARM");

        assertEquals("ALARM", result.get("scenario"));

        verify(demoDeviceService)
                .setScenario(1083L, "ALARM");
    }

    @Test
    void scenariosEndpointListsKnownScenarios() {

        Map<String, Object> result =
                controller.scenarios();

        Set<String> scenarios =
                (Set<String>) result.get("scenarios");

        assertTrue(scenarios.contains("ALARM"));
        assertTrue(scenarios.contains("SHORT_SPIKE"));
        assertTrue(scenarios.contains("RISING"));
        assertTrue(scenarios.contains("STALE"));

        assertEquals(1002L, result.get("defaultDeviceId"),
                "默认演示设备应为标准 DEMO 森林监测节点 1002（FS-N-002）");
    }
}
