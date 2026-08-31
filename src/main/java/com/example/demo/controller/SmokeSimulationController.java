package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.service.DemoDeviceService;
import com.example.demo.service.SmokeReportService;
import com.example.demo.vo.SmokeDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 烟雾模拟测试接口（仅管理员可访问）。
 *
 * 模拟数据通过 SmokeReportService
 * 走与真实设备完全相同的链路：
 * SmokeDecisionService → smoke_record → alarm
 */
@RestController
@RequestMapping("/api/test")
public class SmokeSimulationController {

    /**
     * 默认模拟设备（DEMO 标准演示森林监测节点 1002 = FS-N-002，
     * 位于 Z01 北部核心保护区）
     */
    private static final long DEFAULT_DEVICE_ID = 1002L;

    @Autowired
    private SmokeReportService smokeReportService;

    @Autowired
    private DemoDeviceService demoDeviceService;

    @Autowired
    private DeviceMapper deviceMapper;


    /**
     * 向指定设备注入一条烟雾浓度，
     * 走完整上报链路。
     *
     * 示例：
     * /api/test/smoke?deviceId=1002&value=60
     * /api/test/smoke?value=60
     */
    @GetMapping("/smoke")
    public Map<String, Object> simulateSmoke(
            @RequestParam(defaultValue = "1002") Long deviceId,
            @RequestParam double value) {

        if (value < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "烟雾浓度不能为负数"
            );
        }

        Device device = requireDevice(deviceId);

        /*
         * 人工注入烟雾只允许作用于 DEMO 模拟设备。
         *
         * 防止伪造数据污染真实设备的
         * smoke_record / 告警 / 健康状态。
         */
        if (!"DEMO".equals(device.getSourceType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只有DEMO模拟设备支持人工注入烟雾数据"
            );
        }

        /*
         * 构造与烟雾浓度一致的边缘端数据，
         * 避免触发 SENSOR_FAULT 检查。
         */
        double baseline =
                demoDeviceService.getBaseline(deviceId);

        double ratio =
                baseline <= 0 ? 1.0 : value / baseline;

        String edgeState =
                ratio >= 2.0
                        ? "ALARM"
                        : (ratio >= 1.5
                        ? "PREWARNING"
                        : "NORMAL");

        SmokeDecision decision =
                smokeReportService.persistSample(
                        deviceId,
                        value,
                        baseline,
                        ratio,
                        edgeState
                );

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("smokeValue", value);
        result.put("cloudState", decision.getState());
        result.put("riskScore", decision.getRiskScore());
        result.put("reason", decision.getReason());

        return result;
    }


    /**
     * 切换（或查询）指定 DEMO 设备的场景。
     *
     * 设置：
     * /api/test/scenario?deviceId=1002&scenario=ALARM
     *
     * 查询：
     * /api/test/scenario?deviceId=1002
     */
    @GetMapping("/scenario")
    public Map<String, Object> scenario(
            @RequestParam(defaultValue = "1002") Long deviceId,
            @RequestParam(required = false) String scenario) {

        Device device = requireDevice(deviceId);

        if (!"DEMO".equals(device.getSourceType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只有DEMO模拟设备支持场景切换"
            );
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        if (StringUtils.hasText(scenario)) {

            demoDeviceService.setScenario(
                    deviceId,
                    scenario.trim()
            );

            result.put("success", true);
            result.put("message", "场景已切换");
        }

        result.put("deviceId", deviceId);
        result.put("scenario", demoDeviceService.getScenario(deviceId));

        return result;
    }


    /**
     * 可用的 DEMO 场景列表。
     *
     * GET /api/test/scenarios
     */
    @GetMapping("/scenarios")
    public Map<String, Object> scenarios() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "scenarios",
                DemoDeviceService.getScenarios()
        );

        result.put(
                "defaultDeviceId",
                DEFAULT_DEVICE_ID
        );

        return result;
    }


    private Device requireDevice(Long deviceId) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "设备不存在"
            );
        }

        return device;
    }
}
