package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AncientTree;
import com.example.demo.entity.Device;
import com.example.demo.entity.DroneMission;
import com.example.demo.entity.ForestZone;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.WildlifeHabitat;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.DroneMissionMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 森林公园 SVG 指挥地图数据。
 *
 * 返回 1000×650 坐标系的纯数据分层：
 * 分区（含中心点 / 火险等级）→ 监测节点 → 古树 → 栖息地
 * → 活动火险事件 → 在途无人机。
 *
 * 前端按固定图层渲染，不依赖任何外部地图服务。
 */
@Service
public class ForestMapService {

    private final DeviceMapper deviceMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final AlarmMapper alarmMapper;
    private final AncientTreeMapper ancientTreeMapper;
    private final WildlifeHabitatMapper wildlifeHabitatMapper;
    private final DroneMissionMapper droneMissionMapper;
    private final ForestZoneService forestZoneService;

    public ForestMapService(DeviceMapper deviceMapper,
                            SmokeRecordMapper smokeRecordMapper,
                            AlarmMapper alarmMapper,
                            AncientTreeMapper ancientTreeMapper,
                            WildlifeHabitatMapper wildlifeHabitatMapper,
                            DroneMissionMapper droneMissionMapper,
                            ForestZoneService forestZoneService) {
        this.deviceMapper = deviceMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.alarmMapper = alarmMapper;
        this.ancientTreeMapper = ancientTreeMapper;
        this.wildlifeHabitatMapper = wildlifeHabitatMapper;
        this.droneMissionMapper = droneMissionMapper;
        this.forestZoneService = forestZoneService;
    }

    /**
     * SVG 指挥地图全部图层数据。
     */
    public Map<String, Object> map() {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewBox", "0 0 1000 650");
        result.put("zones", zones());
        result.put("nodes", nodes());
        result.put("trees", trees());
        result.put("habitats", habitats());
        result.put("events", activeEvents());
        result.put("drones", activeDrones());

        return result;
    }

    private List<Map<String, Object>> zones() {

        List<Map<String, Object>> list = new ArrayList<>();

        for (ForestZone zone : forestZoneService.listZones()) {

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", zone.getId());
            item.put("zoneCode", zone.getZoneCode());
            item.put("zoneName", zone.getZoneName());
            item.put("mapCenterX", zone.getMapCenterX());
            item.put("mapCenterY", zone.getMapCenterY());
            item.put("riskLevel", zone.getRiskLevel());

            list.add(item);
        }

        return list;
    }

    /**
     * 森林监测节点 + 最新云端判定状态。
     */
    private List<Map<String, Object>> nodes() {

        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .isNotNull(Device::getNodeCode)
                        .orderByAsc(Device::getDeviceId)
        );

        Map<Long, SmokeRecord> latestMap = new HashMap<>();

        for (SmokeRecord record
                : smokeRecordMapper.selectLatestPerDevice()) {

            latestMap.put(record.getDeviceId(), record);
        }

        List<Map<String, Object>> list = new ArrayList<>();

        for (Device device : devices) {

            SmokeRecord latest = latestMap.get(device.getDeviceId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", device.getDeviceId());
            item.put("zoneId", device.getZoneId());
            item.put("zoneName",
                    forestZoneService.zoneNameById(device.getZoneId()));
            item.put("nodeCode", device.getNodeCode());
            item.put("nodeName", device.getNodeName());
            item.put("mapX", device.getMapX());
            item.put("mapY", device.getMapY());
            item.put("latitude", device.getLatitude());
            item.put("longitude", device.getLongitude());
            item.put("status", device.getStatus());
            item.put("healthStatus", device.getHealthStatus());
            item.put("sourceType", device.getSourceType());
            item.put("lastReportTime", device.getLastReportTime());
            item.put("cloudState",
                    latest == null ? null : latest.getCloudState());
            item.put("edgeState",
                    latest == null ? null : latest.getEdgeState());
            item.put("riskScore",
                    latest == null ? null : latest.getRiskScore());

            list.add(item);
        }

        return list;
    }

    private List<AncientTree> trees() {

        return ancientTreeMapper.selectList(
                new LambdaQueryWrapper<AncientTree>()
                        .orderByAsc(AncientTree::getId)
        );
    }

    private List<WildlifeHabitat> habitats() {

        return wildlifeHabitatMapper.selectList(
                new LambdaQueryWrapper<WildlifeHabitat>()
                        .orderByAsc(WildlifeHabitat::getId)
        );
    }

    /**
     * 活动火险事件（recover_time IS NULL），按优先级总分降序。
     */
    private List<Map<String, Object>> activeEvents() {

        List<Alarm> events = alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getSceneType, AlarmService.SCENE_FOREST)
                        .isNull(Alarm::getRecoverTime)
        );

        events.sort((a, b) -> Integer.compare(
                nullSafe(b.getPriorityScore()),
                nullSafe(a.getPriorityScore())));

        List<Map<String, Object>> list = new ArrayList<>();

        for (Alarm alarm : events) {

            Device device = alarm.getDeviceId() == null
                    ? null : deviceMapper.selectById(alarm.getDeviceId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("alarmId", alarm.getId());
            item.put("deviceId", alarm.getDeviceId());
            item.put("zoneId", alarm.getZoneId());
            item.put("zoneName",
                    forestZoneService.zoneNameById(alarm.getZoneId()));
            item.put("nodeCode", device == null ? null : device.getNodeCode());
            item.put("mapX", device == null ? null : device.getMapX());
            item.put("mapY", device == null ? null : device.getMapY());
            item.put("priorityScore", alarm.getPriorityScore());
            item.put("priorityLevel", alarm.getPriorityLevel());
            item.put("fireConfidenceScore", alarm.getFireConfidenceScore());
            item.put("fireWeatherScore", alarm.getFireWeatherScore());
            item.put("droneConfirmed", alarm.getDroneConfirmed());
            item.put("alarmTime", alarm.getAlarmTime());

            list.add(item);
        }

        return list;
    }

    /**
     * 在途无人机任务（未 COMPLETED），按创建时间倒序。
     */
    private List<Map<String, Object>> activeDrones() {

        List<DroneMission> missions = droneMissionMapper.selectList(
                new LambdaQueryWrapper<DroneMission>()
                        .ne(DroneMission::getState,
                                DroneMissionService.STATE_COMPLETED)
                        .orderByDesc(DroneMission::getId)
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (DroneMission mission : missions) {

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("missionId", mission.getId());
            item.put("missionCode", mission.getMissionCode());
            item.put("droneId", mission.getDroneId());
            item.put("state", mission.getState());
            item.put("missionType", mission.getMissionType());
            item.put("zoneId", mission.getZoneId());
            item.put("zoneName", mission.getZoneName());
            item.put("targetNodeCode", mission.getTargetNodeCode());
            item.put("targetMapX", mission.getTargetMapX());
            item.put("targetMapY", mission.getTargetMapY());
            item.put("confirmedFire", mission.getConfirmedFire());
            item.put("alarmId", mission.getAlarmId());
            item.put("dispatchTime", mission.getDispatchTime());

            list.add(item);
        }

        return list;
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
