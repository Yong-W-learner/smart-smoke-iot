package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 远程控制命令安全边界测试。
 *
 * 关键约束：
 * - DEMO 模拟设备不接入华为云 IoTDA，严禁对其下发远程命令；
 * - REAL 设备必须绑定 iot_device_id 才能下发；
 * - 离线设备禁止执行实时控制命令。
 */
@ExtendWith(MockitoExtension.class)
public class AdminDeviceCommandControllerTest {

    @Mock
    private IoTDAClient ioTDAClient;

    @Mock
    private DeviceMapper deviceMapper;

    @InjectMocks
    private AdminDeviceCommandController controller;

    private Device device(Long id, String sourceType,
                          String iotDeviceId, Integer status) {
        Device device = new Device();
        device.setDeviceId(id);
        device.setSourceType(sourceType);
        device.setIotDeviceId(iotDeviceId);
        device.setStatus(status);
        return device;
    }

    private Map<String, Object> beepOn() {
        Map<String, Object> body = new HashMap<>();
        body.put("target", "BEEP");
        body.put("state", "ON");
        return body;
    }

    @Test
    void demoDeviceRejectedFromRemoteCommand() {

        when(deviceMapper.selectById(1083L))
                .thenReturn(device(1083L, "DEMO", "iot-demo", 1));

        ResponseStatusException e = assertThrows(
                ResponseStatusException.class,
                () -> controller.sendCommand(1083L, beepOn())
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getReason().contains("DEMO"),
                "拒绝原因应明确指出 DEMO 模拟设备");

        verify(ioTDAClient, never()).createCommand(any());
    }

    @Test
    void realDeviceWithoutCloudBindingRejected() {

        when(deviceMapper.selectById(1L))
                .thenReturn(device(1L, "REAL", null, 1));

        ResponseStatusException e = assertThrows(
                ResponseStatusException.class,
                () -> controller.sendCommand(1L, beepOn())
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getReason().contains("未绑定"));

        verify(ioTDAClient, never()).createCommand(any());
    }

    @Test
    void realDeviceOfflineRejectedFromRealtimeCommand() {

        when(deviceMapper.selectById(1L))
                .thenReturn(device(1L, "REAL", "iot-real", 0));

        ResponseStatusException e = assertThrows(
                ResponseStatusException.class,
                () -> controller.sendCommand(1L, beepOn())
        );

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertTrue(e.getReason().contains("离线"));

        verify(ioTDAClient, never()).createCommand(any());
    }

    /* ==================================================
       legacy 兼容回退：仅允许名单内 REAL 设备使用全局兜底
       ================================================== */

    /**
     * legacy 名单内的 REAL 设备（迁移后规范化 device_id=1001）
     * 未绑定 iot_device_id 时，允许回退到旧配置兜底值。
     */
    @Test
    void legacyRealDeviceFallsBackToConfiguredCloudId() {

        ReflectionTestUtils.setField(controller,
                "legacyFallbackDbDeviceIds", "1001");
        ReflectionTestUtils.setField(controller,
                "fallbackCloudDeviceId", "iot-legacy-1");

        when(deviceMapper.selectById(1001L))
                .thenReturn(device(1001L, "REAL", null, 1));
        when(ioTDAClient.createCommand(any()))
                .thenReturn(new CreateCommandResponse());

        Map<String, Object> result =
                controller.sendCommand(1001L, beepOn());

        assertEquals(Boolean.TRUE, result.get("success"));
        verify(ioTDAClient).createCommand(any());
    }

    /**
     * 非 legacy 的 REAL 设备未绑定 iot_device_id 时，
     * 即使配置了全局兜底值也必须拒绝，
     * 禁止把任意设备的命令错误路由到兜底设备。
     */
    @Test
    void nonLegacyRealDeviceWithoutCloudBindingRejectedDespiteFallback() {

        ReflectionTestUtils.setField(controller,
                "legacyFallbackDbDeviceIds", "1001");
        ReflectionTestUtils.setField(controller,
                "fallbackCloudDeviceId", "iot-legacy-1");

        when(deviceMapper.selectById(999L))
                .thenReturn(device(999L, "REAL", null, 1));

        ResponseStatusException e = assertThrows(
                ResponseStatusException.class,
                () -> controller.sendCommand(999L, beepOn())
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getReason().contains("未绑定"),
                "非 legacy REAL 设备不得使用全局兜底设备ID");

        verify(ioTDAClient, never()).createCommand(any());
    }
}
