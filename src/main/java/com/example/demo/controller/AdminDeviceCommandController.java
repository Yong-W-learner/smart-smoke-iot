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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员远程控制真实烟感设备。
 *
 * 当前系统只有一台真实 BearPi 设备，数据库 deviceId 默认是 1。
 */
@RestController
@RequestMapping("/api/admin/devices")
public class AdminDeviceCommandController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminDeviceCommandController.class);

    private final IoTDAClient ioTDAClient;
    private final DeviceMapper deviceMapper;

    @Value("${huawei.iot.deviceId}")
    private String cloudDeviceId;

    @Value("${huawei.iot.localDbDeviceId:1}")
    private Long localDbDeviceId;

    public AdminDeviceCommandController(IoTDAClient ioTDAClient,
                                        DeviceMapper deviceMapper) {
        this.ioTDAClient = ioTDAClient;
        this.deviceMapper = deviceMapper;
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

        if (!deviceId.equals(localDbDeviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前只有设备ID " + localDbDeviceId + " 绑定了真实硬件"
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
