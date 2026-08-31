package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AlarmService {

    private static final Logger log =
            LoggerFactory.getLogger(AlarmService.class);

    @Autowired
    private AlarmMapper alarmMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private AlarmEventLogService eventLogService;

    @Autowired
    private ForestFireRiskService forestFireRiskService;

    @Autowired
    private FireWeatherService fireWeatherService;

    @Autowired
    private ForestEventPriorityService forestEventPriorityService;

    @Autowired
    private ForestZoneService forestZoneService;

    /** 森林火险事件场景标识 */
    public static final String SCENE_FOREST = "FOREST";

    /** 历史宿舍事件场景标识 */
    public static final String SCENE_DORM_LEGACY = "DORM_LEGACY";


    /**
     * ==========================================
     * 处理烟雾判定结果
     * ==========================================
     *
     * cloudState = ALARM：
     *     如果没有活动告警，则创建一条新告警
     *
     * cloudState = NORMAL：
     *     如果存在活动告警，则填写 recoverTime
     *
     * 注意：
     *
     * acknowledged
     * 只表示有没有人确认告警。
     *
     * recoverTime
     * 才表示烟雾环境是否已经恢复。
     *
     * 森林设备（zone_id 非空）创建的告警会同步写入
     * 火险可信度 / 气象评分 / 生态影响评分 / 事件优先级，
     * 并标记 scene_type=FOREST。
     */
    /**
     * 处理烟雾判定结果（兼容调用：不携带风险评分与边缘端状态）。
     */
    public void handleSmokeDecision(
            Long deviceId,
            String cloudState,
            String reason) {

        handleSmokeDecision(
                deviceId,
                cloudState,
                reason,
                null,
                null
        );
    }


    /**
     * 处理烟雾判定结果（携带风险评分与边缘端状态，
     * 用于森林火险事件的火险可信度评分）。
     *
     * @param riskScore 烟雾异常证据分（SmokeDecision.riskScore，可空）
     * @param edgeState 边缘端状态 NORMAL/PREWARNING/ALARM（可空）
     */
    public void handleSmokeDecision(
            Long deviceId,
            String cloudState,
            String reason,
            Double riskScore,
            String edgeState) {

        /*
         * ======================================
         * 1. 查询当前设备是否存在
         * ======================================
         */

        Device device =
                deviceMapper.selectById(
                        deviceId
                );


        if (device == null) {

            log.warn(
                    "设备不存在，无法处理烟雾告警：deviceId={}",
                    deviceId
            );

            return;
        }


        /*
         * ======================================
         * 2. 查询当前尚未恢复的烟雾告警
         * ======================================
         *
         * recover_time IS NULL
         *
         * 表示：
         * 该告警仍然处于活动状态。
         */

        LambdaQueryWrapper<Alarm> wrapper =
                new LambdaQueryWrapper<>();


        wrapper.eq(
                        Alarm::getDeviceId,
                        deviceId
                )
                .eq(
                        Alarm::getAlarmType,
                        "SMOKE"
                )
                .isNull(
                        Alarm::getRecoverTime
                )
                .orderByDesc(
                        Alarm::getAlarmTime
                )
                .last(
                        "LIMIT 1"
                );


        Alarm activeAlarm =
                alarmMapper.selectOne(
                        wrapper
                );


        /*
         * ======================================
         * 3. 云端判定为 ALARM
         * ======================================
         */

        if ("ALARM".equals(
                cloudState)) {


            /*
             * 已经存在活动告警：
             *
             * 不重复创建。
             */
            if (activeAlarm != null) {

                log.debug(
                        "设备已有活动烟雾告警，不重复创建：deviceId={}，alarmId={}",
                        deviceId,
                        activeAlarm.getId()
                );

                return;
            }


            /*
             * ==================================
             * 创建新的烟雾告警
             * ==================================
             */

            Alarm alarm =
                    new Alarm();


            alarm.setDeviceId(
                    deviceId
            );


            alarm.setAlarmTime(
                    LocalDateTime.now()
            );


            /*
             * 生成设备位置。
             *
             * 森林设备：如 北部核心保护区 · FS-N-001
             * 历史宿舍设备：如 1栋1层101室（兼容保留）
             */
            String location =
                    buildLocation(
                            device
                    );


            alarm.setLocation(
                    location
            );


            /*
             * ==================================
             * 场景标记 + 森林火险评分
             * ==================================
             *
             * 设备已映射到森林分区（zone_id 非空）：
             * 标记 scene_type=FOREST，并按透明规则计算
             * 火险可信度 / 气象评分 / 生态影响评分 / 事件优先级。
             *
             * 历史宿舍设备：标记 DORM_LEGACY，不参与森林评分。
             */
            Long zoneId = device.getZoneId();

            if (zoneId != null) {

                fillForestFields(alarm, device, zoneId,
                        cloudState, edgeState, riskScore);
            } else {

                alarm.setSceneType(SCENE_DORM_LEGACY);
            }


            /*
             * ==================================
             * 人工确认状态
             * ==================================
             *
             * 新告警刚产生时：
             *
             * acknowledged = 0
             * 表示还没有人确认。
             */
            alarm.setAcknowledged(
                    0
            );


            /*
             * 尚未人工确认，
             * 所以确认时间为空。
             */
            alarm.setAckTime(
                    null
            );


            /*
             * 告警类型
             */
            alarm.setAlarmType(
                    "SMOKE"
            );


            /*
             * 告警等级
             */
            alarm.setAlarmLevel(
                    "ALARM"
            );


            /*
             * 云端判定原因
             */
            alarm.setReason(
                    reason
            );


            /*
             * 新告警产生时，
             * 环境还没有恢复。
             */
            alarm.setRecoverTime(
                    null
            );


            alarmMapper.insert(
                    alarm
            );


            /*
             * 新告警的初始处置状态：NEW
             */
            alarm.setDisposalState(
                    AlarmDisposalService.STATE_NEW
            );

            alarmMapper.updateById(
                    alarm
            );


            /*
             * 记录时间线节点：系统发现异常并创建告警。
             */
            eventLogService.record(
                    alarm.getId(),
                    deviceId,
                    AlarmDisposalService.EV_ALARM_CREATED,
                    "系统发现异常并创建告警",
                    reason,
                    null
            );


            log.warn(
                    "创建烟雾告警：deviceId={}，alarmId={}，location={}，acknowledged=0，reason={}",
                    deviceId,
                    alarm.getId(),
                    location,
                    reason
            );


            return;
        }


        /*
         * ======================================
         * 4. 云端恢复 NORMAL
         * ======================================
         *
         * 如果当前存在活动烟雾告警，
         * 填写 recover_time。
         *
         * 注意：
         *
         * 不修改 acknowledged。
         *
         * 因为：
         *
         * 是否有人确认
         * 和
         * 环境是否恢复
         *
         * 是两个不同维度。
         */

        if ("NORMAL".equals(
                cloudState)
                && activeAlarm != null) {


            activeAlarm.setRecoverTime(
                    LocalDateTime.now()
            );


            alarmMapper.updateById(
                    activeAlarm
            );


            /*
             * 记录时间线节点：环境恢复正常（系统自动闭环）。
             *
             * 注意：这里不修改处置状态 / acknowledged，
             * 人工处置进度仍由管理员操作推进。
             */
            eventLogService.record(
                    activeAlarm.getId(),
                    deviceId,
                    AlarmDisposalService.EV_SMOKE_RECOVERED,
                    "环境恢复正常",
                    "烟雾浓度已连续恢复至正常范围，系统自动记录恢复",
                    null
            );


            log.info(
                    "烟雾告警恢复：deviceId={}，alarmId={}，acknowledged={}，recoverTime={}",
                    deviceId,
                    activeAlarm.getId(),
                    activeAlarm.getAcknowledged(),
                    activeAlarm.getRecoverTime()
            );
        }
    }


    /**
     * ==========================================
     * 生成设备位置文字
     * ==========================================
     *
     * 森林设备（zone_id 非空）：
     * 格式 = 分区名称 + " · " + 节点编号
     * 例如：北部核心保护区 · FS-N-001
     *
     * 历史宿舍设备：
     * 格式 = X栋X层X室（兼容保留）
     */
    private String buildLocation(
            Device device) {

        if (device == null) {

            return "未知位置";
        }

        if (device.getZoneId() != null) {

            String zoneName =
                    forestZoneService.zoneNameById(
                            device.getZoneId()
                    );

            String nodeCode = device.getNodeCode();

            if (zoneName.isEmpty()) {

                return nodeCode != null
                        ? "森林监测节点" + nodeCode
                        : "森林监测节点" + device.getDeviceId();
            }

            return zoneName
                    + " · "
                    + (nodeCode != null
                    ? nodeCode
                    : "FS-N-" + device.getDeviceId());
        }


        Integer building =
                device.getBuilding();

        Integer floor =
                device.getFloor();

        Integer room =
                device.getRoom();


        /*
         * 三个位置字段都有值
         */
        if (building != null
                && floor != null
                && room != null) {

            return building
                    + "栋"
                    + floor
                    + "层"
                    + room
                    + "室";
        }


        /*
         * 位置信息不完整时，
         * 至少保留设备编号。
         */
        return "设备"
                + device.getDeviceId();
    }


    /**
     * 填充森林火险事件的评分字段（透明规则）。
     */
    private void fillForestFields(
            Alarm alarm,
            Device device,
            Long zoneId,
            String cloudState,
            String edgeState,
            Double riskScore) {

        alarm.setSceneType(SCENE_FOREST);
        alarm.setZoneId(zoneId);

        boolean droneConfirmed = false;

        int fireConfidence =
                forestFireRiskService.fireConfidenceScore(
                        riskScore,
                        cloudState,
                        edgeState,
                        droneConfirmed
                );

        int fireWeather =
                fireWeatherService.score(zoneId);

        ForestEventPriorityService.ImpactScores impacts =
                forestEventPriorityService.impactScores(
                        zoneId,
                        device.getLatitude(),
                        device.getLongitude()
                );

        int priority =
                forestEventPriorityService.priorityScore(
                        fireConfidence,
                        fireWeather,
                        impacts.ancientTreeImpactScore(),
                        impacts.wildlifeImpactScore(),
                        droneConfirmed
                );

        alarm.setFireConfidenceScore(fireConfidence);
        alarm.setFireWeatherScore(fireWeather);
        alarm.setAncientTreeImpactScore(
                impacts.ancientTreeImpactScore());
        alarm.setWildlifeImpactScore(
                impacts.wildlifeImpactScore());
        alarm.setPriorityScore(priority);
        alarm.setPriorityLevel(
                forestEventPriorityService.priorityLevel(priority));
        alarm.setPriorityReason(
                forestEventPriorityService.priorityReason(
                        fireConfidence,
                        fireWeather,
                        impacts.ancientTreeImpactScore(),
                        impacts.wildlifeImpactScore(),
                        priority,
                        droneConfirmed
                ));
        alarm.setDroneConfirmed(0);
    }
}