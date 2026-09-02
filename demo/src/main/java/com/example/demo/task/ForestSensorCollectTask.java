package com.example.demo.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 森林公园真机同步任务：把华为云唯一真机 smoke001（小熊派烟感）的烟雾浓度，
 * 实时回写到森林项目的传感器节点 GT-01（银杏区主节点）。
 *
 * 华为云 smoke001 只上报 Smoke_Value（烟雾浓度），因此真机仅能提供 smoke；
 * 温湿度、CO 等仍由前端/仿真数据维持。GT-01 对应设备台账里的「真机」来源。
 */
@Component
public class ForestSensorCollectTask {

    private static final Logger log = LoggerFactory.getLogger(ForestSensorCollectTask.class);

    // 森林项目里接华为云的真机节点编号（设备台账 GT-01 / 华为云 smoke001）
    private static final String REAL_SENSOR_ID = "GT-01";

    @Autowired
    private IoTDAClient ioTDAClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${huawei.iot.deviceId}")
    private String deviceId;

    @Scheduled(fixedRate = 2000)
    public void syncRealSensor() {
        try {
            boolean online = readOnline();
            double smoke = readSmokeValue();
            String status = online ? (smoke > 70 ? "alarm" : smoke > 50 ? "warning" : "normal") : "offline";
            jdbcTemplate.update(
                    "UPDATE forest_sensor_node SET smoke=?, online=?, status=?, updated_at=NOW() WHERE id=?",
                    smoke, online ? 1 : 0, status, REAL_SENSOR_ID);
            jdbcTemplate.update(
                    "INSERT INTO forest_sensor_reading(sensor_id,collect_time,smoke,source) VALUES(?,NOW(),?,'real')",
                    REAL_SENSOR_ID, smoke);
            log.debug("真机 {} 已同步华为云烟雾 {} ppm，状态={}", REAL_SENSOR_ID, smoke, status);
        } catch (Exception e) {
            // 华为云瞬时异常或设备离线：标记离线，保留上一次烟雾值
            jdbcTemplate.update(
                    "UPDATE forest_sensor_node SET online=0, status='offline', updated_at=NOW() WHERE id=?",
                    REAL_SENSOR_ID);
            log.debug("真机 {} 同步失败：{}", REAL_SENSOR_ID, e.getMessage());
        }
    }

    private boolean readOnline() {
        ShowDeviceRequest request = new ShowDeviceRequest();
        request.setDeviceId(deviceId);
        ShowDeviceResponse resp = ioTDAClient.showDevice(request);
        return "ONLINE".equals(resp.getStatus());
    }

    private double readSmokeValue() {
        ShowDeviceShadowRequest request = new ShowDeviceShadowRequest();
        request.setDeviceId(deviceId);
        ShowDeviceShadowResponse resp = ioTDAClient.showDeviceShadow(request);
        JSONObject root = JSON.parseObject(JSON.toJSONString(resp));
        JSONArray shadowList = root.getJSONArray("shadow");
        JSONObject reported = shadowList.getJSONObject(0).getJSONObject("reported");
        JSONObject props = reported.getJSONObject("properties");
        return Double.parseDouble(props.getString("Smoke_Value"));
    }
}
