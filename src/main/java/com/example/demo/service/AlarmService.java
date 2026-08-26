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
     */
    public void handleSmokeDecision(
            Long deviceId,
            String cloudState,
            String reason) {

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
             * 例如：
             * 1栋1层101室
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
     */
    private String buildLocation(
            Device device) {

        if (device == null) {

            return "未知位置";
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
}