package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AlarmFeedback;
import com.example.demo.mapper.AlarmFeedbackMapper;
import com.example.demo.mapper.AlarmMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/experience")
public class AdminExperienceController {

    private final AlarmFeedbackMapper feedbackMapper;
    private final AlarmMapper alarmMapper;

    public AdminExperienceController(AlarmFeedbackMapper feedbackMapper,
                                     AlarmMapper alarmMapper) {
        this.feedbackMapper = feedbackMapper;
        this.alarmMapper = alarmMapper;
    }

    @GetMapping("/feedback-summary")
    public Map<String, Object> feedbackSummary() {
        List<AlarmFeedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<AlarmFeedback>()
                        .orderByDesc(AlarmFeedback::getFeedbackTime)
        );

        Long closedAlarmCount = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .isNotNull(Alarm::getRecoverTime)
                        .eq(Alarm::getAlarmType, "SMOKE")
        );

        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("REAL_SMOKE", 0L);
        distribution.put("COOKING", 0L);
        distribution.put("SMOKING", 0L);
        distribution.put("STEAM", 0L);
        distribution.put("FALSE_ALARM", 0L);
        distribution.put("UNKNOWN", 0L);

        for (AlarmFeedback feedback : feedbacks) {
            String type = feedback.getFeedbackType();
            distribution.put(type, distribution.getOrDefault(type, 0L) + 1L);
        }

        double feedbackRate = closedAlarmCount == null || closedAlarmCount == 0
                ? 0.0
                : Math.round((feedbacks.size() * 1000.0 / closedAlarmCount)) / 10.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feedbackCount", feedbacks.size());
        result.put("closedAlarmCount", closedAlarmCount);
        result.put("feedbackRate", feedbackRate);
        result.put("distribution", distribution);
        result.put("latestFeedback", feedbacks.isEmpty() ? null : feedbacks.get(0));
        return result;
    }
}
