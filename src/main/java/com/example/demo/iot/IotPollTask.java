package com.example.demo.iot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IotPollTask {

    @Autowired
    private IoTDAClient ioTDAClient;

    @Value("${huawei.iot.deviceId}")
    private String deviceId;

    //@Scheduled(fixedRate = 2000)
    public void pollShadow() {
        try {
            if (ioTDAClient == null) {
                System.out.println("IoTDAClient未初始化");
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

            System.out.println("=====烟感采集数据=====");
            System.out.println("烟雾浓度：" + smokeValue);
            System.out.println("蜂鸣器状态：" + beepStatus);

            //报警判断，阈值举例：大于50触发报警
            double smoke = Double.parseDouble(smokeValue);
            if(smoke > 50){
                System.out.println("⚠️⚠️⚠️烟雾超标，触发报警！");
            }

        } catch (Exception e) {
            System.err.println("读取影子异常：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
