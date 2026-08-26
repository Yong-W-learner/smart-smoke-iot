package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.SmokeRecordMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员烟雾监测历史查询。
 */
@RestController
@RequestMapping("/api/admin/records")
public class AdminRecordController {

    private final SmokeRecordMapper smokeRecordMapper;

    public AdminRecordController(SmokeRecordMapper smokeRecordMapper) {
        this.smokeRecordMapper = smokeRecordMapper;
    }

    @GetMapping
    public List<SmokeRecord> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String cloudState,
            @RequestParam(defaultValue = "50") Integer limit) {

        LambdaQueryWrapper<SmokeRecord> wrapper = new LambdaQueryWrapper<>();

        if (deviceId != null) {
            wrapper.eq(SmokeRecord::getDeviceId, deviceId);
        }

        if (StringUtils.hasText(cloudState)
                && !"all".equalsIgnoreCase(cloudState)) {
            wrapper.eq(SmokeRecord::getCloudState, cloudState.trim());
        }

        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));

        wrapper.orderByDesc(SmokeRecord::getCollectTime)
                .last("LIMIT " + safeLimit);

        return smokeRecordMapper.selectList(wrapper);
    }
}
