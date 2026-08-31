package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.service.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向用户的设备健康解释接口。
 *
 * 这是透明的规则评分，不冒充机器学习预测：
 * 连接、传感器状态、数据新鲜度、连续通信失败共同构成健康指数。
 */
@RestController
@RequestMapping("/api/device")
public class DeviceInsightController {

    private final DeviceMapper deviceMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final DataScopeService dataScopeService;

    public DeviceInsightController(DeviceMapper deviceMapper,
                                   SmokeRecordMapper smokeRecordMapper,
                                   DataScopeService dataScopeService) {
        this.deviceMapper = deviceMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/{deviceId}/insight")
    public Map<String, Object> insight(@PathVariable Long deviceId) {
        dataScopeService.assertCanAccessDevice(deviceId);

        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在");
        }

        SmokeRecord latest = smokeRecordMapper.selectOne(
                new LambdaQueryWrapper<SmokeRecord>()
                        .eq(SmokeRecord::getDeviceId, deviceId)
                        .orderByDesc(SmokeRecord::getCollectTime)
                        .last("LIMIT 1")
        );

        boolean online = Integer.valueOf(1).equals(device.getStatus());
        String health = device.getHealthStatus() == null ? "UNKNOWN" : device.getHealthStatus();

        long ageSeconds = device.getLastReportTime() == null
                ? Long.MAX_VALUE
                : Math.max(0, Duration.between(device.getLastReportTime(), LocalDateTime.now()).getSeconds());

        int networkScore = online ? 100 : 30;
        int sensorScore = "SENSOR_FAULT".equalsIgnoreCase(health) ? 15 : 100;
        int freshnessScore;
        if (ageSeconds <= 10) freshnessScore = 100;
        else if (ageSeconds <= 30) freshnessScore = 82;
        else if (ageSeconds <= 120) freshnessScore = 55;
        else freshnessScore = 25;

        if ("STALE".equalsIgnoreCase(health)) freshnessScore = Math.min(freshnessScore, 55);
        if ("OFFLINE".equalsIgnoreCase(health)) networkScore = Math.min(networkScore, 25);

        int failurePenalty = Math.min(24, Math.max(0, device.getConsecutiveFailures() == null ? 0 : device.getConsecutiveFailures()) * 8);
        int overall = (int) Math.round(networkScore * 0.35 + sensorScore * 0.40 + freshnessScore * 0.25) - failurePenalty;
        overall = Math.max(0, Math.min(100, overall));

        String label = overall >= 90 ? "Excellent"
                : overall >= 75 ? "Healthy"
                : overall >= 55 ? "Attention"
                : "Needs inspection";

        List<String> recommendations = new ArrayList<>();
        if (!online) recommendations.add("检查设备供电、Wi-Fi 或 IoTDA 连接状态");
        if ("SENSOR_FAULT".equalsIgnoreCase(health)) recommendations.add("检查 MQ-2 传感器连接与采样状态");
        if (freshnessScore < 70) recommendations.add("监测数据新鲜度下降，建议检查上报链路");
        if (failurePenalty > 0) recommendations.add("近期存在连续通信失败，建议观察网络稳定性");
        if (recommendations.isEmpty()) recommendations.add("设备运行稳定，暂无维护建议");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("network", networkScore);
        components.put("sensor", sensorScore);
        components.put("freshness", freshnessScore);
        components.put("consecutiveFailures", device.getConsecutiveFailures() == null ? 0 : device.getConsecutiveFailures());
        components.put("lastReportAgeSeconds", ageSeconds == Long.MAX_VALUE ? null : ageSeconds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", deviceId);
        result.put("score", overall);
        result.put("label", label);
        result.put("healthStatus", health);
        result.put("online", online);
        result.put("components", components);
        result.put("recommendations", recommendations);
        result.put("latestRecord", latest);
        return result;
    }
}
