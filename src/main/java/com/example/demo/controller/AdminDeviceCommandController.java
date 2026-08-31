package com.example.demo.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandRequest;
import com.huaweicloud.sdk.iotda.v5.model.CreateCommandResponse;
import com.huaweicloud.sdk.iotda.v5.model.DeviceCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员远程控制真实烟感设备。
 *
 * 支持绑定 iot_device_id 且在线的 REAL 设备。
 * 华为云设备ID以 device 表 iot_device_id 为准；
 * 仅指定的 legacy REAL 设备（旧版单设备迁移）允许使用
 * application.yml 兜底值回退，不再对任意 REAL 设备无条件兜底。
 */
@RestController
@RequestMapping("/api/admin/devices")
public class AdminDeviceCommandController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminDeviceCommandController.class);

    private final IoTDAClient ioTDAClient;
    private final DeviceMapper deviceMapper;

    /**
     * 兼容旧配置的华为云设备ID兜底值。
     *
     * 仅当设备属于 {@link #legacyFallbackDbDeviceIds} 名单内
     * 且 device 表 iot_device_id 为空时，才回退到这里；
     * 其他 REAL 设备必须显式绑定 iot_device_id。
     */
    @Value("${huawei.iot.deviceId:}")
    private String fallbackCloudDeviceId;

    /**
     * 允许兼容回退的 legacy REAL 数据库设备ID（逗号分隔）。
     *
     * 默认仅允许原单设备迁移的规范化设备 1001。
     */
    @Value("${huawei.iot.legacyFallbackDbDeviceIds:1001}")
    private String legacyFallbackDbDeviceIds;

    public AdminDeviceCommandController(IoTDAClient ioTDAClient,
                                        DeviceMapper deviceMapper) {
        this.ioTDAClient = ioTDAClient;
        this.deviceMapper = deviceMapper;
    }

    /**
     * 设备是否在 legacy 兼容回退名单内。
     */
    private boolean isLegacyFallbackAllowed(Long deviceId) {

        if (deviceId == null
                || legacyFallbackDbDeviceIds == null) {

            return false;
        }

        for (String token : legacyFallbackDbDeviceIds.split(",")) {

            if (String.valueOf(deviceId).trim()
                    .equals(token.trim())) {

                return true;
            }
        }

        return false;
    }

    /**
     * POST /api/admin/devices/{deviceId}/command
     *
     * body:
     * {
     *   "target": "BEEP" | "LED",
     *   "state": "ON" | "OFF"
     * }
     */
    @PostMapping("/{deviceId}/command")
    public Map<String, Object> sendCommand(
            @PathVariable Long deviceId,
            @RequestBody Map<String, Object> body) {

        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "设备不存在"
            );
        }

        /*
         * DEMO 模拟设备不接入华为云 IoTDA，
         * 严禁对其下发远程命令（LED / 蜂鸣器）。
         * 仅 REAL 设备允许远程控制。
         */
        if (!"REAL".equalsIgnoreCase(device.getSourceType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "DEMO模拟设备不支持远程控制"
            );
        }

        /*
         * 设备必须绑定 IoTDA 设备ID，才能下发命令。
         *
         * 优先使用 device 表中的 iot_device_id。
         * 仅 legacy 名单内的 REAL 设备允许使用旧配置兜底值；
         * 其他 REAL 设备未绑定 iot_device_id 一律拒绝，
         * 避免把任意设备的命令错误路由到全局兜底设备。
         */
        String cloudDeviceId = device.getIotDeviceId();

        if (!StringUtils.hasText(cloudDeviceId)
                && isLegacyFallbackAllowed(deviceId)) {

            cloudDeviceId = fallbackCloudDeviceId;
        }

        if (!StringUtils.hasText(cloudDeviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "设备未绑定IoTDA设备ID，无法下发命令"
            );
        }

        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "设备当前离线，无法执行实时控制命令"
            );
        }

        String target = body.get("target") == null
                ? ""
                : String.valueOf(body.get("target")).trim().toUpperCase();

        String state = body.get("state") == null
                ? ""
                : String.valueOf(body.get("state")).trim().toUpperCase();

        if (!"ON".equals(state) && !"OFF".equals(state)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "state 只能是 ON 或 OFF"
            );
        }

        String commandName;
        String paras;

        if ("BEEP".equals(target)) {
            commandName = "Smoke_Control_Beep";
            paras = "{\"Beep\":\"" + state + "\"}";

        } else if ("LED".equals(target)) {
            commandName = "Smoke_Control_LED";
            paras = "{\"LED\":\"" + state + "\"}";

        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "target 只能是 BEEP 或 LED"
            );
        }

        try {
            CreateCommandRequest request = new CreateCommandRequest();
            request.withDeviceId(cloudDeviceId);

            DeviceCommandRequest command = new DeviceCommandRequest();
            command.withServiceId("Smoke");
            command.withCommandName(commandName);
            command.withParas(paras);

            request.withBody(command);

            CreateCommandResponse response =
                    ioTDAClient.createCommand(request);

            JSONObject responseJson =
                    JSON.parseObject(JSON.toJSONString(response));

            log.info(
                    "管理员下发设备命令成功：dbDeviceId={}, target={}, state={}",
                    deviceId,
                    target,
                    state
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", target + " 已下发 " + state + " 命令");
            result.put("deviceId", deviceId);
            result.put("target", target);
            result.put("state", state);
            result.put("commandId", responseJson.getString("command_id"));
            result.put("cloudResponse", responseJson.get("response"));
            return result;

        } catch (Exception e) {
            log.error(
                    "管理员下发设备命令失败：dbDeviceId={}, target={}, state={}",
                    deviceId,
                    target,
                    state,
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "设备命令下发失败，请检查设备在线状态和华为云连接"
            );
        }
    }
}
