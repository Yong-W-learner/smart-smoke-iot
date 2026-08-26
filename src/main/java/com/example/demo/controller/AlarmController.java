package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.service.DataScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AlarmController {

    private final AlarmMapper alarmMapper;
    private final DataScopeService dataScopeService;

    public AlarmController(AlarmMapper alarmMapper,
                           DataScopeService dataScopeService) {
        this.alarmMapper = alarmMapper;
        this.dataScopeService = dataScopeService;
    }

    /**
     * resident：只能看到本人房间设备告警。
     * admin：可以看到全部告警。
     */
    @GetMapping("/alarm/list")
    public List<Alarm> alarmList() {
        List<Long> deviceIds = dataScopeService.getVisibleDeviceIds();

        if (deviceIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Alarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Alarm::getDeviceId, deviceIds)
                .orderByDesc(Alarm::getAlarmTime);

        return alarmMapper.selectList(wrapper);
    }
}
