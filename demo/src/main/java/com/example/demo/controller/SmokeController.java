package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.DeviceStatusHistory;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.DeviceStatusHistoryMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SmokeController {
    @Autowired
    private SmokeRecordMapper smokeRecordMapper;

    @Autowired
    private DeviceStatusHistoryMapper statusHistoryMapper;

    @Autowired
    private IoTDAClient ioTDAClient;

    @Value("${huawei.iot.deviceId}")
    private String deviceId;

    @GetMapping("/latest")
    public SmokeRecord getLatest(@RequestParam(required = false) Long deviceId){
        LambdaQueryWrapper<SmokeRecord> wrapper=new LambdaQueryWrapper<>();
        if (deviceId != null) wrapper.eq(SmokeRecord::getDeviceId, deviceId);
        wrapper.orderByDesc(SmokeRecord::getCollectTime).last("LIMIT 1");
        return smokeRecordMapper.selectOne(wrapper);
    }

    @GetMapping("/history")
    public List<SmokeRecord> getHistory(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Integer limit){
        LambdaQueryWrapper<SmokeRecord> wrapper=new LambdaQueryWrapper<>();
        if (deviceId != null) wrapper.eq(SmokeRecord::getDeviceId, deviceId);
        if (startTime != null) wrapper.ge(SmokeRecord::getCollectTime, startTime);
        if (endTime != null) wrapper.le(SmokeRecord::getCollectTime, endTime);
        // 新增 limit 参数：按条数取最近 N 条（缺省仍最多 5000），供实时曲线等场景使用
        int n = (limit == null || limit <= 0) ? 5000 : Math.min(limit, 5000);
        wrapper.orderByDesc(SmokeRecord::getCollectTime).last("LIMIT " + n);
        return smokeRecordMapper.selectList(wrapper);
    }

    // 查询某设备的告警记录（alarm > 0），供“历史告警”列表使用。
    // 单独按告警过滤，避免告警记录被最新的正常采集记录挤出 LIMIT。
    @GetMapping("/history/alarm")
    public List<SmokeRecord> getAlarmHistory(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime){
        LambdaQueryWrapper<SmokeRecord> wrapper=new LambdaQueryWrapper<>();
        if (deviceId != null) wrapper.eq(SmokeRecord::getDeviceId, deviceId);
        if (startTime != null) wrapper.ge(SmokeRecord::getCollectTime, startTime);
        if (endTime != null) wrapper.le(SmokeRecord::getCollectTime, endTime);
        wrapper.gt(SmokeRecord::getAlarm, 0)
               .orderByDesc(SmokeRecord::getCollectTime)
               .last("LIMIT 2000");
        return smokeRecordMapper.selectList(wrapper);
    }

    // 查询设备在线状态变更历史（按设备编号过滤，倒序返回）
    @GetMapping("/device/status/history")
    public List<DeviceStatusHistory> getStatusHistory(
            @RequestParam(required = false) Long deviceId) {
        LambdaQueryWrapper<DeviceStatusHistory> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) wrapper.eq(DeviceStatusHistory::getDeviceId, deviceId);
        wrapper.orderByDesc(DeviceStatusHistory::getChangeTime);
        return statusHistoryMapper.selectList(wrapper);
    }

    // 查询设备在线状态（调用华为云 showDevice，官方返回 ONLINE/OFFLINE）
    @GetMapping("/device/status")
    public Map<String, Object> getDeviceStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        try {
            ShowDeviceRequest request = new ShowDeviceRequest();
            request.setDeviceId(deviceId);
            ShowDeviceResponse resp = ioTDAClient.showDevice(request);
            String status = resp.getStatus();
            result.put("status", status);
            result.put("online", "ONLINE".equals(status));
            result.put("updateTime", resp.getConnectionStatusUpdateTime());
        } catch (Exception e) {
            result.put("status", "UNKNOWN");
            result.put("online", false);
            result.put("updateTime", null);
        }
        return result;
    }
}
