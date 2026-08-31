package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.mapper.AlarmMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员告警查询与筛选。
 */
@RestController
@RequestMapping("/api/admin/alarms")
public class AdminAlarmController {

    private final AlarmMapper alarmMapper;

    public AdminAlarmController(AlarmMapper alarmMapper) {
        this.alarmMapper = alarmMapper;
    }

    @GetMapping
    public List<Alarm> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer acknowledged,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") Integer limit) {

        LambdaQueryWrapper<Alarm> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            if ("active".equalsIgnoreCase(status)) {
                wrapper.isNull(Alarm::getRecoverTime);
            } else if ("recovered".equalsIgnoreCase(status)) {
                wrapper.isNotNull(Alarm::getRecoverTime);
            }
        }

        if (acknowledged != null
                && (acknowledged == 0 || acknowledged == 1)) {
            wrapper.eq(Alarm::getAcknowledged, acknowledged);
        }

        if (deviceId != null) {
            wrapper.eq(Alarm::getDeviceId, deviceId);
        }

        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            wrapper.and(w -> w
                    .like(Alarm::getLocation, k)
                    .or()
                    .like(Alarm::getReason, k));
        }

        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));

        wrapper.orderByDesc(Alarm::getAlarmTime)
                .last("LIMIT " + safeLimit);

        return alarmMapper.selectList(wrapper);
    }
}
