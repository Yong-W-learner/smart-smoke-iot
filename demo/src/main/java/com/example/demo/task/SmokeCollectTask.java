package com.example.demo.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SmokeCollectTask {

    private static final Logger log = LoggerFactory.getLogger(SmokeCollectTask.class);

    // —— 误报抑制参数 ——
    // 连续超阈值采样次数达到该值，才把警情升级为真正的报警（避免单点尖峰误报）
    private static final int HIGH_COUNT_REQUIRED = 3;
    // 报警后，连续恢复正常采样次数达到该值，才解除报警（避免报警状态来回抖动）
    private static final int RECOVERY_COUNT_REQUIRED = 3;

    @Autowired
    private IoTDAClient ioTDAClient;

    @Autowired
    private SmokeRecordMapper smokeRecordMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private AlarmMapper alarmMapper;

    @Value("${huawei.iot.deviceId}")
    private String deviceId;

    // 上次影子的上报时间戳，用于判断设备是否上报了新数据（离线后 event_time 会冻结）
    private volatile String lastEventTime = null;

    // 误报抑制状态（本任务只采集真机 device_id=1，@Scheduled 单线程执行，无需加锁）
    private final DebounceState debounce = new DebounceState();

    @Scheduled(fixedRate = 2000)
    public void collectSmokeData() {
        try {
            ShowDeviceShadowRequest request = new ShowDeviceShadowRequest();
            request.setDeviceId(deviceId);
            ShowDeviceShadowResponse resp = ioTDAClient.showDeviceShadow(request);
            String jsonStr = JSON.toJSONString(resp);
            JSONObject root = JSON.parseObject(jsonStr);
            JSONArray shadowList = root.getJSONArray("shadow");
            JSONObject shadow = shadowList.getJSONObject(0);
            JSONObject reported = shadow.getJSONObject("reported");

            // 设备离线后 event_time（最后上报时间）会冻结；若和上次相同则说明没有新数据，跳过写库
            String eventTime = reported.getString("event_time");
            if (eventTime != null && eventTime.equals(lastEventTime)) {
                return;
            }
            lastEventTime = eventTime;

            JSONObject props = reported.getJSONObject("properties");

            String smokeVal = props.getString("Smoke_Value");
            //String beepStatus = props.getString("BeepStatus"); //历史记录表去掉beepStatus字段

            SmokeRecord record = new SmokeRecord();
            //⭐单板固定设备编号
            record.setDeviceId(1L);
            //⭐采集时间
            record.setCollectTime(LocalDateTime.now());
            //⭐修改字段名 smokeConcentration
            record.setSmokeConcentration(Double.parseDouble(smokeVal));

            //警情三级判断：0正常 1一级(>50) 2二级(>70) 3三级(>90)
            double smokeNum = Double.parseDouble(smokeVal);
            int rawLevel;
            if(smokeNum > 90){
                rawLevel = 3;
            }else if(smokeNum > 70){
                rawLevel = 2;
            }else if(smokeNum > 50){
                rawLevel = 1;
            }else{
                rawLevel = 0;
            }

            // 二次判断（误报抑制）：
            // 单点尖峰不直接报警，需连续 HIGH_COUNT_REQUIRED 次超阈值才升级；
            // 报警后需连续 RECOVERY_COUNT_REQUIRED 次正常才解除，避免来回抖动。
            boolean wasArmed = debounce.armedLevel > 0;
            int alarm = debounce.judge(rawLevel);
            record.setAlarm(alarm);

            // 报警上升沿（未报警→已报警）：生成独立警情事件，不进入设备运维工单
            if (alarm > 0 && !wasArmed) {
                createAlarmIncident(alarm);
            }

            if (rawLevel > 0 && alarm == 0) {
                log.debug("烟雾浓度 {} 单点/暂态超标，连续异常 {}/{} 次，暂不报警", smokeVal, debounce.highCount, HIGH_COUNT_REQUIRED);
            }

            smokeRecordMapper.insert(record);

            log.debug("烟感采集数据：烟雾浓度={}，告警标记={}", smokeVal, record.getAlarm());

        } catch (Exception e) {
            // 判断是不是程序中断停止，减少打印堆栈
            String msg = e.getMessage();
            if(msg != null && msg.contains("interrupted")){
                log.info("采集任务被中断（程序停止）");
            }else {
                log.error("烟感数据采集异常", e);
            }
        }
    }

    /**
     * 报警上升沿：自动生成一条待处置警情（每次报警事件仅一条）。
     * 采集任务固定采集 device_id=1 的真机，警情位置取自设备安装位置。
     */
    private void createAlarmIncident(int level) {
        try {
            Device device = deviceMapper.selectById(1L);
            if (device == null) {
                return;
            }
            String levelText = level == 3 ? "三级" : level == 2 ? "二级" : "一级";
            Alarm incident = new Alarm();
            incident.setDeviceId(device.getDeviceId());
            incident.setAlarmTime(LocalDateTime.now());
            incident.setLocation(device.getBuilding() + "栋 " + device.getFloor() + "层 " + device.getRoom() + "户");
            incident.setStatus(0);
            incident.setProcessStatus("pending");
            alarmMapper.insert(incident);
            log.info("触发{}警情，已生成待处置事件，事件编号={}", levelText, incident.getId());
        } catch (Exception e) {
            log.error("生成警情事件失败", e);
        }
    }

    /**
     * 误报抑制状态机。
     *
     * 对应「短窗口连续异常次数 + 恢复条件」的二次判断思路：
     * 1. 单个采样超阈值只计数，不立即报警（确认期）；
     * 2. 连续 HIGH_COUNT_REQUIRED 次超阈值，才锁定为报警，并取区间内最高等级；
     * 3. 报警期间出现更高浓度，等级会继续上升；
     * 4. 报警后需连续 RECOVERY_COUNT_REQUIRED 次恢复正常，才解除报警，避免抖动。
     */
    private static class DebounceState {
        int highCount = 0;      // 连续超阈值次数
        int recoverCount = 0;   // 报警后连续恢复正常次数
        int armedLevel = 0;     // 当前已锁定的报警等级（0=未报警）
        int maxLevelInRun = 0;  // 当前连续超阈值区间内的最高等级

        int judge(int rawLevel) {
            if (rawLevel > 0) {
                highCount++;
                recoverCount = 0;
                maxLevelInRun = Math.max(maxLevelInRun, rawLevel);
                if (armedLevel > 0) {
                    // 已在报警中：保持并可升级等级
                    armedLevel = Math.max(armedLevel, maxLevelInRun);
                    return armedLevel;
                }
                if (highCount >= HIGH_COUNT_REQUIRED) {
                    armedLevel = maxLevelInRun;
                    return armedLevel;
                }
                return 0; // 确认期，暂不报警
            } else {
                highCount = 0;
                maxLevelInRun = 0;
                if (armedLevel > 0) {
                    recoverCount++;
                    if (recoverCount >= RECOVERY_COUNT_REQUIRED) {
                        armedLevel = 0;
                        recoverCount = 0;
                        return 0; // 连续恢复正常，解除报警
                    }
                    return armedLevel; // 恢复期，保持报警
                }
                return 0;
            }
        }
    }
}
