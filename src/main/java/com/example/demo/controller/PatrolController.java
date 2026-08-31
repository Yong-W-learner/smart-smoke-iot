package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.entity.ForestZone;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.EnvironmentRecordMapper;
import com.example.demo.service.AlarmService;
import com.example.demo.service.DataScopeService;
import com.example.demo.service.FireWeatherService;
import com.example.demo.service.ForestZoneService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 森林巡护员移动端接口。
 *
 * 仅巡护员（patrol）与管理（admin）可访问；
 * 巡护员只能看到自己绑定巡护区域（zone_id）内的数据。
 *
 * 所有判定均为透明规则，不含 AI 描述。
 */
@RestController
@RequestMapping("/api/patrol")
public class PatrolController {

    private final DataScopeService dataScopeService;
    private final ForestZoneService forestZoneService;
    private final FireWeatherService fireWeatherService;
    private final DeviceMapper deviceMapper;
    private final AlarmMapper alarmMapper;
    private final EnvironmentRecordMapper environmentRecordMapper;

    public PatrolController(DataScopeService dataScopeService,
                            ForestZoneService forestZoneService,
                            FireWeatherService fireWeatherService,
                            DeviceMapper deviceMapper,
                            AlarmMapper alarmMapper,
                            EnvironmentRecordMapper environmentRecordMapper) {
        this.dataScopeService = dataScopeService;
        this.forestZoneService = forestZoneService;
        this.fireWeatherService = fireWeatherService;
        this.deviceMapper = deviceMapper;
        this.alarmMapper = alarmMapper;
        this.environmentRecordMapper = environmentRecordMapper;
    }

    /**
     * 巡护员首页：本人巡护分区 + 火险气象 + 节点状态 + 活动事件摘要。
     *
     * GET /api/patrol/home
     */
    @GetMapping("/home")
    public Map<String, Object> home() {

        User user = requirePatrol();
        Long zoneId = user.getZoneId();

        if (zoneId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "巡护员尚未绑定巡护区域，请联系管理员配置"
            );
        }

        ForestZone zone = forestZoneService.getZone(zoneId);

        if (zone == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "巡护区域不存在"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zone", zone);
        result.put("weather", zoneWeather(zoneId));
        result.put("nodeStats", nodeStats(zoneId));
        result.put("activeEventCount", activeEventCount(zoneId));
        result.put("recentEvents", recentEvents(zoneId, 5));

        return result;
    }

    /**
     * 巡护员活动火险事件（本人巡护分区内，环境未恢复）。
     *
     * GET /api/patrol/events
     */
    @GetMapping("/events")
    public List<Map<String, Object>> events() {

        User user = requirePatrol();

        if (user.getZoneId() == null) {
            return new ArrayList<>();
        }

        return recentEvents(user.getZoneId(), 50);
    }

    /* ==================== 私有 ==================== */

    private User requirePatrol() {

        User user = dataScopeService.getCurrentUser();

        if (!dataScopeService.isPatrol(user)
                && !dataScopeService.isAdmin(user)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "仅巡护员可访问巡护端"
            );
        }

        return user;
    }

    private Map<String, Object> zoneWeather(Long zoneId) {

        int score = fireWeatherService.score(zoneId);

        EnvironmentRecord latest = environmentRecordMapper.selectOne(
                new LambdaQueryWrapper<EnvironmentRecord>()
                        .eq(EnvironmentRecord::getZoneId, zoneId)
                        .orderByDesc(EnvironmentRecord::getRecordTime)
                        .last("LIMIT 1")
        );

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fireWeatherScore", score);
        item.put("temperature",
                latest == null ? null : latest.getTemperature());
        item.put("humidity",
                latest == null ? null : latest.getHumidity());
        item.put("soilMoisture",
                latest == null ? null : latest.getSoilMoisture());
        item.put("windSpeed",
                latest == null ? null : latest.getWindSpeed());
        item.put("rainfallMm",
                latest == null ? null : latest.getRainfallMm());
        item.put("recordTime",
                latest == null ? null : latest.getRecordTime());

        return item;
    }

    private Map<String, Object> nodeStats(Long zoneId) {

        List<Device> nodes = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getZoneId, zoneId)
        );

        int online = 0;
        int offline = 0;
        int stale = 0;
        int sensorFault = 0;

        for (Device node : nodes) {

            if (Integer.valueOf(1).equals(node.getStatus())) {
                online++;
            } else {
                offline++;
            }

            String health = node.getHealthStatus();

            if ("STALE".equals(health)) {
                stale++;
            } else if ("SENSOR_FAULT".equals(health)) {
                sensorFault++;
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", nodes.size());
        stats.put("online", online);
        stats.put("offline", offline);
        stats.put("stale", stale);
        stats.put("sensorFault", sensorFault);
        return stats;
    }

    private long activeEventCount(Long zoneId) {

        return alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getSceneType, AlarmService.SCENE_FOREST)
                        .eq(Alarm::getZoneId, zoneId)
                        .isNull(Alarm::getRecoverTime)
        );
    }

    private List<Map<String, Object>> recentEvents(Long zoneId, int limit) {

        List<Alarm> events = alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getSceneType, AlarmService.SCENE_FOREST)
                        .eq(Alarm::getZoneId, zoneId)
                        .isNull(Alarm::getRecoverTime)
                        .orderByDesc(Alarm::getPriorityScore)
                        .orderByDesc(Alarm::getAlarmTime)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100)))
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (Alarm event : events) {

            Device device = event.getDeviceId() == null
                    ? null : deviceMapper.selectById(event.getDeviceId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", event.getId());
            item.put("alarmTime", event.getAlarmTime());
            item.put("reason", event.getReason());
            item.put("priorityScore", event.getPriorityScore());
            item.put("priorityLevel", event.getPriorityLevel());
            item.put("fireConfidenceScore", event.getFireConfidenceScore());
            item.put("fireWeatherScore", event.getFireWeatherScore());
            item.put("droneConfirmed", event.getDroneConfirmed());
            item.put("disposalState", event.getDisposalState());
            item.put("nodeCode",
                    device == null ? null : device.getNodeCode());
            item.put("nodeName",
                    device == null ? null : device.getNodeName());
            item.put("mapX",
                    device == null ? null : device.getMapX());
            item.put("mapY",
                    device == null ? null : device.getMapY());

            list.add(item);
        }

        return list;
    }
}
