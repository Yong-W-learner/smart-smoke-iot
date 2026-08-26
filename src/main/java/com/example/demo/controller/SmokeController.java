package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.service.DataScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class SmokeController {

    private final SmokeRecordMapper smokeRecordMapper;
    private final DataScopeService dataScopeService;

    public SmokeController(SmokeRecordMapper smokeRecordMapper,
                           DataScopeService dataScopeService) {
        this.smokeRecordMapper = smokeRecordMapper;
        this.dataScopeService = dataScopeService;
    }

    /**
     * 当前用户可见设备中的最新一条烟雾记录。
     */
    @GetMapping("/latest")
    public SmokeRecord getLatest() {
        List<Long> deviceIds = dataScopeService.getVisibleDeviceIds();

        if (deviceIds.isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<SmokeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SmokeRecord::getDeviceId, deviceIds)
                .orderByDesc(SmokeRecord::getCollectTime)
                .last("LIMIT 1");

        return smokeRecordMapper.selectOne(wrapper);
    }

    /**
     * 当前用户可见设备最近20条烟雾记录。
     */
    @GetMapping("/history")
    public List<SmokeRecord> getHistory() {
        List<Long> deviceIds = dataScopeService.getVisibleDeviceIds();

        if (deviceIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<SmokeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SmokeRecord::getDeviceId, deviceIds)
                .orderByDesc(SmokeRecord::getCollectTime)
                .last("LIMIT 20");

        return smokeRecordMapper.selectList(wrapper);
    }
}
