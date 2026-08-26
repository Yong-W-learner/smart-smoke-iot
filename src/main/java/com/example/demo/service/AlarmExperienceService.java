package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AlarmFeedback;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmFeedbackMapper;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 安全事件体验服务：
 * 1. 用户事后反馈
 * 2. 事件复盘指标
 */
@Service
public class AlarmExperienceService {

    private static final Set<String> ALLOWED_TYPES = new LinkedHashSet<>(Arrays.asList(
            "REAL_SMOKE",
            "COOKING",
            "SMOKING",
            "STEAM",
            "FALSE_ALARM",
            "UNKNOWN"
    ));

    private final AlarmMapper alarmMapper;
    private final AlarmFeedbackMapper feedbackMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final DataScopeService dataScopeService;

    public AlarmExperienceService(AlarmMapper alarmMapper,
                                  AlarmFeedbackMapper feedbackMapper,
                                  SmokeRecordMapper smokeRecordMapper,
                                  DataScopeService dataScopeService) {
        this.alarmMapper = alarmMapper;
        this.feedbackMapper = feedbackMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.dataScopeService = dataScopeService;
    }

    public Alarm requireAccessibleAlarm(Long alarmId) {
        Alarm alarm = alarmMapper.selectById(alarmId);
        if (alarm == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警记录不存在");
        }
        dataScopeService.assertCanAccessDevice(alarm.getDeviceId());
        return alarm;
    }

    public AlarmFeedback getFeedback(Long alarmId) {
        return feedbackMapper.selectOne(
                new LambdaQueryWrapper<AlarmFeedback>()
                        .eq(AlarmFeedback::getAlarmId, alarmId)
                        .last("LIMIT 1")
        );
    }

    public AlarmFeedback saveFeedback(Long alarmId,
                                      String feedbackType,
                                      String feedbackNote) {
        Alarm alarm = requireAccessibleAlarm(alarmId);

        if (alarm.getRecoverTime() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "安全事件尚未恢复，恢复后再进行事件反馈"
            );
        }

        String type = feedbackType == null
                ? ""
                : feedbackType.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈类型不支持"
            );
        }

        String note = feedbackNote == null ? null : feedbackNote.trim();
        if (StringUtils.hasText(note) && note.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "补充说明不能超过255个字符"
            );
        }

        User currentUser = dataScopeService.getCurrentUser();
        AlarmFeedback feedback = getFeedback(alarmId);

        if (feedback == null) {
            feedback = new AlarmFeedback();
            feedback.setAlarmId(alarmId);
        }

        feedback.setUserId(currentUser.getId());
        feedback.setFeedbackType(type);
        feedback.setFeedbackNote(StringUtils.hasText(note) ? note : null);
        feedback.setFeedbackTime(LocalDateTime.now());

        if (feedback.getId() == null) {
            feedbackMapper.insert(feedback);
        } else {
            feedbackMapper.updateById(feedback);
        }

        return getFeedback(alarmId);
    }

    public Map<String, Object> buildReview(Long alarmId) {
        Alarm alarm = requireAccessibleAlarm(alarmId);

        LocalDateTime start = alarm.getAlarmTime();
        LocalDateTime end = alarm.getRecoverTime() != null
                ? alarm.getRecoverTime()
                : LocalDateTime.now();

        // 多取30秒上下文，便于复盘异常开始和恢复过程。
        LocalDateTime queryStart = start.minusSeconds(30);
        LocalDateTime queryEnd = end.plusSeconds(30);

        List<SmokeRecord> records = smokeRecordMapper.selectList(
                new LambdaQueryWrapper<SmokeRecord>()
                        .eq(SmokeRecord::getDeviceId, alarm.getDeviceId())
                        .ge(SmokeRecord::getCollectTime, queryStart)
                        .le(SmokeRecord::getCollectTime, queryEnd)
                        .orderByAsc(SmokeRecord::getCollectTime)
        );

        double peakSmoke = 0.0;
        double peakRatio = 0.0;
        double peakRisk = 0.0;
        int warningSamples = 0;
        int alarmSamples = 0;

        LocalDateTime firstWarningTime = null;
        LocalDateTime firstAlarmSampleTime = null;

        for (SmokeRecord record : records) {
            if (record.getSmokeConcentration() != null) {
                peakSmoke = Math.max(peakSmoke, record.getSmokeConcentration());
            }
            if (record.getSmokeRatio() != null) {
                peakRatio = Math.max(peakRatio, record.getSmokeRatio());
            }
            if (record.getRiskScore() != null) {
                peakRisk = Math.max(peakRisk, record.getRiskScore());
            }

            String cloudState = record.getCloudState();
            if ("WARNING".equalsIgnoreCase(cloudState)) {
                warningSamples++;
                if (firstWarningTime == null) {
                    firstWarningTime = record.getCollectTime();
                }
            }
            if ("ALARM".equalsIgnoreCase(cloudState)) {
                alarmSamples++;
                if (firstAlarmSampleTime == null) {
                    firstAlarmSampleTime = record.getCollectTime();
                }
            }
        }

        long durationSeconds = Math.max(0, Duration.between(start, end).getSeconds());
        Long responseSeconds = alarm.getAckTime() == null
                ? null
                : Math.max(0, Duration.between(start, alarm.getAckTime()).getSeconds());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("durationSeconds", durationSeconds);
        metrics.put("responseSeconds", responseSeconds);
        metrics.put("peakSmokeConcentration", round(peakSmoke, 3));
        metrics.put("peakSmokeRatio", round(peakRatio, 3));
        metrics.put("peakRiskScore", round(peakRisk, 2));
        metrics.put("sampleCount", records.size());
        metrics.put("warningSamples", warningSamples);
        metrics.put("alarmSamples", alarmSamples);
        metrics.put("firstWarningTime", firstWarningTime);
        metrics.put("firstAlarmSampleTime", firstAlarmSampleTime);

        List<Map<String, Object>> timeline = new ArrayList<>();
        if (firstWarningTime != null) {
            timeline.add(timelineItem(firstWarningTime, "WARNING", "系统发现异常趋势", "开始进入持续性观察阶段"));
        }
        timeline.add(timelineItem(alarm.getAlarmTime(), "ALARM", "持续异常被确认为安全事件", alarm.getReason()));
        if (alarm.getAckTime() != null) {
            timeline.add(timelineItem(alarm.getAckTime(), "ACK", "用户已确认收到告警", "人工响应已记录"));
        }
        if (alarm.getRecoverTime() != null) {
            timeline.add(timelineItem(alarm.getRecoverTime(), "RECOVER", "环境恢复至安全状态", "事件完成闭环"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alarm", alarm);
        result.put("metrics", metrics);
        result.put("timeline", timeline);
        result.put("feedback", getFeedback(alarmId));
        return result;
    }

    private Map<String, Object> timelineItem(LocalDateTime time,
                                             String type,
                                             String title,
                                             String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("time", time);
        item.put("type", type);
        item.put("title", title);
        item.put("description", description);
        return item;
    }

    private double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }
}
