package com.example.demo.iot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IotPollTask {

    private static final Logger log = LoggerFactory.getLogger(IotPollTask.class);

    @Autowired
    private IoTDAClient ioTDAClient;

    @Value("${huawei.iot.deviceId}")
    private String deviceId;

    @Scheduled(fixedRate = 2000)
    public void pollShadow() {
        try {
            if (ioTDAClient == null) {
                log.debug("IoTDAClient未初始化");
                return;
            }

            ShowDeviceShadowRequest request = new ShowDeviceShadowRequest();
            request.setDeviceId(deviceId);

            ShowDeviceShadowResponse resp = ioTDAClient.showDeviceShadow(request);
            String jsonStr = JSON.toJSONString(resp);

            JSONObject root = JSON.parseObject(jsonStr);
            //重点：shadow是数组，取第0个元素
            JSONArray shadowArray = root.getJSONArray("shadow");
            JSONObject shadowItem = shadowArray.getJSONObject(0);
            JSONObject reported = shadowItem.getJSONObject("reported");
            JSONObject properties = reported.getJSONObject("properties");

            String smokeValue = properties.getString("Smoke_Value");
            String beepStatus = properties.getString("BeepStatus");

            log.debug("烟感影子数据：烟雾浓度={}，蜂鸣器状态={}", smokeValue, beepStatus);

            //报警判断，阈值举例：大于50触发报警
            double smoke = Double.parseDouble(smokeValue);
            if(smoke > 50){
                log.debug("烟雾超标：{}", smoke);
            }

        } catch (Exception e) {
            // 该任务仅用于影子轮询诊断，避免云端短暂异常时每2秒刷屏。
            log.debug("读取影子异常：{}", e.getMessage());
        }
    }
}
