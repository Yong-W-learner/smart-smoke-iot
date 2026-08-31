package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.vo.Result;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandRequest;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandResponse;
import com.huaweicloud.sdk.iotda.v5.model.DeviceCommandRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员远程控制真实 BearPi 烟感设备。
 * 命令名称和参数与 demo_v5_1_fusion 中已实现的设备协议保持一致。
 */
@RestController
@RequestMapping("/api/admin/devices")
public class AdminDeviceCommandController {

    private static final Logger log = LoggerFactory.getLogger(AdminDeviceCommandController.class);

    private final IoTDAClient ioTDAClient;
    private final DeviceMapper deviceMapper;

    @Value("${huawei.iot.deviceId}")
    private String cloudDeviceId;

    @Value("${huawei.iot.localDbDeviceId:1}")
    private Long localDbDeviceId;

    public AdminDeviceCommandController(IoTDAClient ioTDAClient, DeviceMapper deviceMapper) {
        this.ioTDAClient = ioTDAClient;
        this.deviceMapper = deviceMapper;
    }

    /**
     * body: { "target": "BEEP" | "LED", "state": "ON" | "OFF" }
     */
    @PostMapping("/{deviceId}/command")
    public Result<Map<String, Object>> sendCommand(
            @PathVariable Long deviceId,
            @RequestBody Map<String, Object> body) {

        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return Result.fail("设备不存在");
        }
        if (!deviceId.equals(localDbDeviceId)) {
            return Result.fail("该设备为仿真设备，不支持远程硬件控制");
        }
        if (!isRealDeviceOnline()) {
            return Result.fail("设备当前离线，无法下发实时控制命令");
        }

        String target = normalized(body.get("target"));
        String state = normalized(body.get("state"));
        if (!"ON".equals(state) && !"OFF".equals(state)) {
            return Result.fail("state 只能是 ON 或 OFF");
        }

        String commandName;
        String parameterName;
        if ("BEEP".equals(target)) {
            commandName = "Smoke_Control_Beep";
            parameterName = "Beep";
        } else if ("LED".equals(target)) {
            commandName = "Smoke_Control_LED";
            parameterName = "LED";
        } else {
            return Result.fail("target 只能是 BEEP 或 LED");
        }

        try {
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put(parameterName, state);

            DeviceCommandRequest command = new DeviceCommandRequest()
                    .withServiceId("Smoke")
                    .withCommandName(commandName)
                    .withParas(parameters);

            CreateCommandRequest request = new CreateCommandRequest()
                    .withDeviceId(cloudDeviceId)
                    .withBody(command);

            CreateCommandResponse response = ioTDAClient.createCommand(request);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deviceId", deviceId);
            result.put("target", target);
            result.put("state", state);
            result.put("commandId", response.getCommandId());
            result.put("deviceResponse", response.getResponse());

            log.info("管理员设备命令下发成功：deviceId={}, target={}, state={}", deviceId, target, state);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("设备命令下发失败：deviceId={}, target={}, state={}", deviceId, target, state, e);
            return Result.fail("命令下发失败，请检查设备连接和华为云产品模型");
        }
    }

    private boolean isRealDeviceOnline() {
        try {
            ShowDeviceRequest request = new ShowDeviceRequest();
            request.setDeviceId(cloudDeviceId);
            ShowDeviceResponse response = ioTDAClient.showDevice(request);
            return "ONLINE".equals(response.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    private String normalized(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase();
    }
}
