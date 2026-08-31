package com.example.demo.task;

import com.example.demo.entity.DeviceStatusHistory;
import com.example.demo.mapper.DeviceStatusHistoryMapper;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 设备在线状态历史记录任务：
 * 每 15 秒查询一次华为云真实在线状态，状态从在线<->离线发生切换时写入一条历史，
 * 供居民端查看设备何时上线、何时离线。只在「状态变更」时落库，避免频繁产生无用记录。
 */
@Component
public class DeviceStatusTask {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusTask.class);

    // 真机固定 device_id=1（与采集任务一致）
    private static final Long REAL_DEVICE_ID = 1L;

    @Autowired
    private IoTDAClient ioTDAClient;

    @Autowired
    private DeviceStatusHistoryMapper statusMapper;

    @Value("${huawei.iot.deviceId}")
    private String huaweiDeviceId;

    // 上次已知在线状态；null 表示首次运行（仅记录状态，不写库，避免启动产生一条伪变更）
    private volatile Boolean lastOnline = null;

    @Scheduled(fixedRate = 15000)
    public void trackOnlineStatus() {
        try {
            boolean online = queryOnline();
            if (lastOnline != null && lastOnline.booleanValue() != online) {
                DeviceStatusHistory h = new DeviceStatusHistory();
                h.setDeviceId(REAL_DEVICE_ID);
                h.setOnline(online ? 1 : 0);
                h.setChangeTime(LocalDateTime.now());
                statusMapper.insert(h);
                log.info("设备在线状态变更：{} -> {}", lastOnline ? "在线" : "离线", online ? "在线" : "离线");
            }
            lastOnline = online;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("interrupted")) {
                log.info("在线状态跟踪任务被中断（程序停止）");
            } else {
                log.error("设备在线状态跟踪异常", e);
            }
        }
    }

    private boolean queryOnline() {
        ShowDeviceRequest request = new ShowDeviceRequest();
        request.setDeviceId(huaweiDeviceId);
        ShowDeviceResponse resp = ioTDAClient.showDevice(request);
        return "ONLINE".equals(resp.getStatus());
    }
}
