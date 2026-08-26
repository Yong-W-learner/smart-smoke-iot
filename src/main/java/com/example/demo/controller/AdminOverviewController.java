package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员总览统计。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {

    private final DeviceMapper deviceMapper;
    private final UserMapper userMapper;
    private final AlarmMapper alarmMapper;
    private final SmokeRecordMapper smokeRecordMapper;

    public AdminOverviewController(DeviceMapper deviceMapper,
                                   UserMapper userMapper,
                                   AlarmMapper alarmMapper,
                                   SmokeRecordMapper smokeRecordMapper) {
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
        this.alarmMapper = alarmMapper;
        this.smokeRecordMapper = smokeRecordMapper;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Long deviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
        );

        Long onlineCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getStatus, 1)
        );

        Long abnormalDeviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .ne(Device::getHealthStatus, "NORMAL")
        );

        Long residentCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "resident")
        );

        Long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "admin")
        );

        Long activeAlarmCount = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .isNull(Alarm::getRecoverTime)
                        .eq(Alarm::getAlarmType, "SMOKE")
        );

        Long unacknowledgedAlarmCount = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .isNull(Alarm::getRecoverTime)
                        .eq(Alarm::getAlarmType, "SMOKE")
                        .eq(Alarm::getAcknowledged, 0)
        );

        SmokeRecord latest = smokeRecordMapper.selectOne(
                new LambdaQueryWrapper<SmokeRecord>()
                        .orderByDesc(SmokeRecord::getCollectTime)
                        .last("LIMIT 1")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceCount", deviceCount);
        result.put("onlineCount", onlineCount);
        result.put("abnormalDeviceCount", abnormalDeviceCount);
        result.put("residentCount", residentCount);
        result.put("adminCount", adminCount);
        result.put("activeAlarmCount", activeAlarmCount);
        result.put("unacknowledgedAlarmCount", unacknowledgedAlarmCount);
        result.put("latestRecord", latest);
        return result;
    }
}
