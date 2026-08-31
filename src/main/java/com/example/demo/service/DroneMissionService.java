package com.example.demo.service;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.DroneMission;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.DroneMissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 无人机巡航任务服务（火险事件现场复核）。
 *
 * 状态机：
 * PLANNED → DISPATCHED → EN_ROUTE → ON_SITE → RETURNED → COMPLETED
 *
 * 透明规则，非AI：
 * - 派发（dispatch）：为火险事件创建复核任务并记录 DRONE_DISPATCHED；
 * - 推进（advance）：按状态机逐步推进，记录 DRONE_EN_ROUTE / DRONE_ARRIVED
 *   / DRONE_COMPLETED；
 * - 确认（confirmFire）：到达现场后由人工确认航拍结果——
 *   确认火点 → 事件 drone_confirmed=1 并重算优先级（强制 ≥95），
 *   记录 DRONE_FIRE_CONFIRMED；
 *   未发现火点 → 保留烟雾异常证据，记录 DRONE_NO_FIRE_FOUND。
 */
@Service
public class DroneMissionService {

    private static final Logger log =
            LoggerFactory.getLogger(DroneMissionService.class);

    /* ========== 任务状态 ========== */
    public static final String STATE_PLANNED = "PLANNED";
    public static final String STATE_DISPATCHED = "DISPATCHED";
    public static final String STATE_EN_ROUTE = "EN_ROUTE";
    public static final String STATE_ON_SITE = "ON_SITE";
    public static final String STATE_RETURNED = "RETURNED";
    public static final String STATE_COMPLETED = "COMPLETED";

    /* ========== 无人机事件日志类型 ========== */
    public static final String EV_DRONE_DISPATCHED = "DRONE_DISPATCHED";
    public static final String EV_DRONE_EN_ROUTE = "DRONE_EN_ROUTE";
    public static final String EV_DRONE_ARRIVED = "DRONE_ARRIVED";
    public static final String EV_DRONE_FIRE_CONFIRMED = "DRONE_FIRE_CONFIRMED";
    public static final String EV_DRONE_NO_FIRE_FOUND = "DRONE_NO_FIRE_FOUND";
    public static final String EV_DRONE_COMPLETED = "DRONE_COMPLETED";

    private static final DateTimeFormatter MISSION_CODE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final DroneMissionMapper droneMissionMapper;
    private final AlarmMapper alarmMapper;
    private final DeviceMapper deviceMapper;
    private final AlarmEventLogService eventLogService;
    private final ForestEventPriorityService forestEventPriorityService;
    private final ForestFireRiskService forestFireRiskService;
    private final ForestZoneService forestZoneService;

    public DroneMissionService(DroneMissionMapper droneMissionMapper,
                               AlarmMapper alarmMapper,
                               DeviceMapper deviceMapper,
                               AlarmEventLogService eventLogService,
                               ForestEventPriorityService forestEventPriorityService,
                               ForestFireRiskService forestFireRiskService,
                               ForestZoneService forestZoneService) {
        this.droneMissionMapper = droneMissionMapper;
        this.alarmMapper = alarmMapper;
        this.deviceMapper = deviceMapper;
        this.eventLogService = eventLogService;
        this.forestEventPriorityService = forestEventPriorityService;
        this.forestFireRiskService = forestFireRiskService;
        this.forestZoneService = forestZoneService;
    }


    /**
     * 为火险事件派发无人机复核任务。
     *
     * 默认使用无人机 DRONE-01，创建任务并直接进入 DISPATCHED。
     */
    public DroneMission dispatch(Long alarmId, String droneId) {

        Alarm alarm = requireAlarm(alarmId);

        Device device =
                deviceMapper.selectById(alarm.getDeviceId());

        DroneMission mission = new DroneMission();
        mission.setMissionCode(newMissionCode());
        mission.setDroneId(droneId == null
                ? "DRONE-01"
                : droneId.trim());
        mission.setZoneId(alarm.getZoneId());
        mission.setZoneName(forestZoneService.zoneNameById(alarm.getZoneId()));
        mission.setTargetNodeCode(device == null
                ? null : device.getNodeCode());
        mission.setMissionType("FIRE_CONFIRM");
        mission.setState(STATE_DISPATCHED);
        mission.setAlarmId(alarm.getId());
        mission.setTargetLat(device == null
                ? null : device.getLatitude());
        mission.setTargetLng(device == null
                ? null : device.getLongitude());
        mission.setTargetMapX(device == null
                ? null : device.getMapX());
        mission.setTargetMapY(device == null
                ? null : device.getMapY());
        mission.setConfirmedFire(0);
        mission.setDispatchTime(LocalDateTime.now());
        mission.setCreatedAt(LocalDateTime.now());

        droneMissionMapper.insert(mission);

        eventLogService.record(
                alarm.getId(),
                alarm.getDeviceId(),
                EV_DRONE_DISPATCHED,
                "无人机已派发",
                mission.getDroneId() + " 已前往 "
                        + (device == null ? "事发区域" : device.getNodeCode())
                        + " 复核",
                null
        );

        log.info("无人机复核任务已派发：missionId={}，alarmId={}，droneId={}",
                mission.getId(), alarm.getId(), mission.getDroneId());

        return mission;
    }


    /**
     * 推进任务状态（PLANNED→DISPATCHED→EN_ROUTE→ON_SITE→RETURNED→COMPLETED）。
     * 已 COMPLETED 时幂等返回。
     */
    public DroneMission advance(Long missionId) {

        DroneMission mission = requireMission(missionId);
        String state = mission.getState();

        switch (state) {

            case STATE_PLANNED:
                mission.setState(STATE_DISPATCHED);
                mission.setDispatchTime(LocalDateTime.now());
                logMissionEvent(mission, EV_DRONE_DISPATCHED,
                        "无人机已派发", "任务进入派发状态");
                break;

            case STATE_DISPATCHED:
                mission.setState(STATE_EN_ROUTE);
                logMissionEvent(mission, EV_DRONE_EN_ROUTE,
                        "无人机前往途中", "无人机正在飞往事发区域");
                break;

            case STATE_EN_ROUTE:
                mission.setState(STATE_ON_SITE);
                mission.setArriveTime(LocalDateTime.now());
                logMissionEvent(mission, EV_DRONE_ARRIVED,
                        "无人机已到达现场", "无人机到达目标监测节点上空");
                break;

            case STATE_ON_SITE:
                mission.setState(STATE_RETURNED);
                mission.setReturnTime(LocalDateTime.now());
                logMissionEvent(mission, EV_DRONE_COMPLETED,
                        "无人机开始返航", "无人机完成现场侦查，开始返航");
                break;

            case STATE_RETURNED:
                mission.setState(STATE_COMPLETED);
                mission.setCompleteTime(LocalDateTime.now());
                logMissionEvent(mission, EV_DRONE_COMPLETED,
                        "无人机任务完成", "无人机已返航，复核任务结束");
                break;

            case STATE_COMPLETED:
                return mission;

            default:
                throw badRequest("未知的无人机任务状态：" + state);
        }

        droneMissionMapper.updateById(mission);
        return mission;
    }


    /**
     * 到达现场后确认航拍结果。
     *
     * confirmed=true  → 事件 drone_confirmed=1，重算优先级（强制 ≥95）；
     * confirmed=false → 保留烟雾异常证据，等待人工复核。
     */
    public DroneMission confirmFire(Long missionId,
                                    boolean confirmed,
                                    String note) {

        DroneMission mission = requireMission(missionId);

        if (!STATE_ON_SITE.equals(mission.getState())
                && !STATE_RETURNED.equals(mission.getState())) {
            throw badRequest("请先让无人机到达现场（当前状态：" + mission.getState() + "）");
        }

        mission.setConfirmedFire(confirmed ? 1 : 0);
        mission.setResultNote(note);

        droneMissionMapper.updateById(mission);

        Alarm alarm = alarmMapper.selectById(mission.getAlarmId());

        if (confirmed) {

            if (alarm != null) {
                applyFireConfirmation(alarm);
            }

            logMissionEvent(mission, EV_DRONE_FIRE_CONFIRMED,
                    "无人机确认火点",
                    note == null ? "航拍确认存在火点，优先级已强制提高" : note);

            log.warn("无人机确认火点：missionId={}，alarmId={}，事件优先级已重算（≥95）",
                    mission.getId(), mission.getAlarmId());

        } else {

            logMissionEvent(mission, EV_DRONE_NO_FIRE_FOUND,
                    "无人机未发现火点",
                    note == null ? "航拍未发现明火，保留烟雾异常证据待人工复核" : note);

            log.info("无人机未发现火点：missionId={}，alarmId={}",
                    mission.getId(), mission.getAlarmId());
        }

        return mission;
    }


    /**
     * 无人机确认火点后：事件 drone_confirmed=1，
     * 火险可信度 +20，并重算优先级（强制 ≥95）。
     */
    private void applyFireConfirmation(Alarm alarm) {

        alarm.setDroneConfirmed(1);

        Integer oldConfidence = alarm.getFireConfidenceScore();

        int newConfidence = Math.min(100,
                (oldConfidence == null ? 0 : oldConfidence)
                        + ForestFireRiskService.DRONE_CONFIRMED_BONUS);

        int weather = nullSafe(alarm.getFireWeatherScore());
        int tree = nullSafe(alarm.getAncientTreeImpactScore());
        int habitat = nullSafe(alarm.getWildlifeImpactScore());

        int priority = forestEventPriorityService.priorityScore(
                newConfidence, weather, tree, habitat, true);

        alarm.setFireConfidenceScore(newConfidence);
        alarm.setPriorityScore(priority);
        alarm.setPriorityLevel(
                forestEventPriorityService.priorityLevel(priority));
        alarm.setPriorityReason(
                forestEventPriorityService.priorityReason(
                        newConfidence, weather, tree, habitat,
                        priority, true));

        alarmMapper.updateById(alarm);
    }


    /* ========== 内部工具 ========== */

    private void logMissionEvent(DroneMission mission, String type,
                                 String label, String description) {

        if (mission.getAlarmId() == null) {
            return;
        }

        Alarm alarm = alarmMapper.selectById(mission.getAlarmId());

        eventLogService.record(
                mission.getAlarmId(),
                alarm == null ? null : alarm.getDeviceId(),
                type,
                label,
                description,
                null
        );
    }

    private String newMissionCode() {

        return "DM-" + LocalDateTime.now().format(MISSION_CODE_FORMAT);
    }

    private Alarm requireAlarm(Long alarmId) {

        Alarm alarm = alarmId == null ? null : alarmMapper.selectById(alarmId);

        if (alarm == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "火险事件不存在"
            );
        }

        return alarm;
    }

    private DroneMission requireMission(Long missionId) {

        DroneMission mission = missionId == null
                ? null : droneMissionMapper.selectById(missionId);

        if (mission == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "无人机任务不存在"
            );
        }

        return mission;
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
