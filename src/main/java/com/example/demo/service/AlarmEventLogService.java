package com.example.demo.service;

import com.example.demo.entity.AlarmEventLog;
import com.example.demo.mapper.AlarmEventLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 安全事件操作日志服务。
 *
 * 记录告警生命周期中的关键节点，
 * 供寝室详情按时间顺序展示完整处置时间线。
 */
@Service
public class AlarmEventLogService {

    private static final Logger log =
            LoggerFactory.getLogger(AlarmEventLogService.class);

    private final AlarmEventLogMapper alarmEventLogMapper;

    public AlarmEventLogService(AlarmEventLogMapper alarmEventLogMapper) {
        this.alarmEventLogMapper = alarmEventLogMapper;
    }

    /**
     * 记录一条事件日志。
     *
     * @param alarmId     关联告警 ID
     * @param deviceId    关联设备 ID
     * @param eventType   机器可读事件类型
     * @param eventLabel  中文标签
     * @param description 补充说明（可空）
     * @param operator    操作人（系统事件传 null）
     */
    public void record(
            Long alarmId,
            Long deviceId,
            String eventType,
            String eventLabel,
            String description,
            String operator) {

        if (alarmId == null || deviceId == null) {
            log.warn("跳过事件日志：告警或设备ID为空，alarmId={}，deviceId={}",
                    alarmId, deviceId);
            return;
        }

        AlarmEventLog entry = new AlarmEventLog();
        entry.setAlarmId(alarmId);
        entry.setDeviceId(deviceId);
        entry.setEventType(eventType);
        entry.setEventLabel(eventLabel);
        entry.setDescription(description);
        entry.setOperator(operator);
        entry.setEventTime(LocalDateTime.now());

        alarmEventLogMapper.insert(entry);

        log.info(
                "记录安全事件日志：alarmId={}，deviceId={}，type={}，operator={}",
                alarmId,
                deviceId,
                eventType,
                operator
        );
    }
}
