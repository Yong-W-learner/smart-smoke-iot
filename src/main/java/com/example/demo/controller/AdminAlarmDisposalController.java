package com.example.demo.controller;

import com.example.demo.entity.Alarm;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.service.AlarmDisposalService;
import com.example.demo.service.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员告警处置接口（受 /api/admin/** 的 ADMIN 权限保护）。
 *
 * 处置流程：
 * 确认告警（/api/alarm/{id}/handle）→ 标记到场 → 填写处置结果 → 关闭事件。
 *
 * 处置状态与 recoverTime（环境恢复）相互独立，
 * 管理员操作不代替系统对烟雾恢复的自动判定。
 */
@RestController
@RequestMapping("/api/admin/alarms")
public class AdminAlarmDisposalController {

    private final AlarmMapper alarmMapper;
    private final AlarmDisposalService alarmDisposalService;
    private final DataScopeService dataScopeService;

    public AdminAlarmDisposalController(AlarmMapper alarmMapper,
                                        AlarmDisposalService alarmDisposalService,
                                        DataScopeService dataScopeService) {
        this.alarmMapper = alarmMapper;
        this.alarmDisposalService = alarmDisposalService;
        this.dataScopeService = dataScopeService;
    }


    /**
     * 标记到场，开始现场处理。
     *
     * POST /api/admin/alarms/{id}/on-site
     */
    @PostMapping("/{id}/on-site")
    public Map<String, Object> onSite(@PathVariable Long id) {

        Alarm alarm = requireAlarm(id);

        Alarm updated =
                alarmDisposalService.markOnSite(
                        alarm,
                        currentOperator()
                );

        return result("已标记到场，开始现场处理", updated);
    }


    /**
     * 填写处置结果。
     *
     * POST /api/admin/alarms/{id}/resolve
     * body: { "remark": "现场无明显烟雾，已排查" }
     *
     * remark 为自由文本，仅作为人工记录，
     * 系统不会根据单条 MQ-2 数据自动宣称吸烟 / 违规电器 / 真实火灾。
     */
    @PostMapping("/{id}/resolve")
    public Map<String, Object> resolve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        Alarm alarm = requireAlarm(id);

        String remark =
                body == null ? null : stringValue(body.get("remark"));

        Alarm updated =
                alarmDisposalService.resolve(
                        alarm,
                        currentOperator(),
                        remark
                );

        return result("已填写处置结果", updated);
    }


    /**
     * 关闭事件。
     *
     * POST /api/admin/alarms/{id}/close
     * body: { "remark": "已完成现场排查并关闭" }
     */
    @PostMapping("/{id}/close")
    public Map<String, Object> close(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        Alarm alarm = requireAlarm(id);

        String remark =
                body == null ? null : stringValue(body.get("remark"));

        Alarm updated =
                alarmDisposalService.close(
                        alarm,
                        currentOperator(),
                        remark
                );

        return result("安全事件已关闭", updated);
    }


    /* ========== 工具 ========== */

    private Alarm requireAlarm(Long id) {

        Alarm alarm = alarmMapper.selectById(id);

        if (alarm == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "告警记录不存在"
            );
        }

        return alarm;
    }

    private String currentOperator() {

        return dataScopeService.getCurrentUser().getUsername();
    }

    private String stringValue(Object value) {

        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> result(
            String message,
            Alarm alarm) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("success", true);
        result.put("message", message);
        result.put("alarm", alarmMapper.selectById(alarm.getId()));

        return result;
    }
}
