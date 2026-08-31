package com.example.demo.controller;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.AlarmFeedback;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.service.AlarmDisposalService;
import com.example.demo.service.AlarmExperienceService;
import com.example.demo.service.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/alarm")
public class AlarmActionController {

    private final AlarmMapper alarmMapper;
    private final DataScopeService dataScopeService;
    private final AlarmExperienceService alarmExperienceService;
    private final AlarmDisposalService alarmDisposalService;

    public AlarmActionController(AlarmMapper alarmMapper,
                                 DataScopeService dataScopeService,
                                 AlarmExperienceService alarmExperienceService,
                                 AlarmDisposalService alarmDisposalService) {
        this.alarmMapper = alarmMapper;
        this.dataScopeService = dataScopeService;
        this.alarmExperienceService = alarmExperienceService;
        this.alarmDisposalService = alarmDisposalService;
    }

    /**
     * 人工确认告警。
     * resident 只能确认自己可访问设备产生的告警；admin 可以确认全部告警。
     *
     * 确认只推进人工处置状态（NEW → ACKNOWLEDGED），
     * 不修改 recoverTime——环境是否恢复仍由系统自动判定。
     */
    @PostMapping("/{id}/handle")
    public Map<String, Object> handleAlarm(@PathVariable Long id) {
        Alarm alarm = alarmMapper.selectById(id);

        if (alarm == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警记录不存在");
        }

        dataScopeService.assertCanAccessDevice(alarm.getDeviceId());

        boolean wasConfirmed =
                Integer.valueOf(1).equals(alarm.getAcknowledged());

        String operator =
                dataScopeService.getCurrentUser().getUsername();

        alarmDisposalService.confirm(alarm, operator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message",
                wasConfirmed ? "该告警已经确认" : "告警确认成功");
        result.put("alarm", alarmMapper.selectById(id));
        return result;
    }

    /**
     * 查询单条告警详情，同样执行数据权限校验。
     */
    @GetMapping("/{id}")
    public Map<String, Object> getAlarm(@PathVariable Long id) {
        Alarm alarm = alarmMapper.selectById(id);

        if (alarm == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警记录不存在");
        }

        dataScopeService.assertCanAccessDevice(alarm.getDeviceId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("alarm", alarm);
        result.put("feedback", alarmExperienceService.getFeedback(id));
        return result;
    }

    /**
     * V5.3：安全事件复盘。
     */
    @GetMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("review", alarmExperienceService.buildReview(id));
        return result;
    }

    /**
     * V5.3：居民/管理员对已结束事件进行反馈。
     */
    @PostMapping("/{id}/feedback")
    public Map<String, Object> feedback(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        String type = body.get("feedbackType") == null
                ? null
                : String.valueOf(body.get("feedbackType"));

        String note = body.get("feedbackNote") == null
                ? null
                : String.valueOf(body.get("feedbackNote"));

        AlarmFeedback feedback = alarmExperienceService.saveFeedback(id, type, note);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "事件反馈已记录");
        result.put("feedback", feedback);
        return result;
    }
}
