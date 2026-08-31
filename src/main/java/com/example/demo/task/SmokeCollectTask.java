package com.example.demo.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.service.DeviceHealthService;
import com.example.demo.service.SmokeReportService;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceShadowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 真实设备（华为云 IoTDA）烟雾采集任务。
 *
 * 每 3 秒遍历数据库中 source_type = REAL
 * 且已绑定 iot_device_id 的设备，
 * 逐台读取设备影子并送入 SmokeReportService 链路。
 *
 * 每台设备独立保存上次已处理的 event_time，
 * 防止重复处理同一条设备影子。
 */
@Component
public class SmokeCollectTask {

    private static final Logger log =
            LoggerFactory.getLogger(SmokeCollectTask.class);

    /**
     * 华为云 IoTDA 客户端
     */
    @Autowired
    private IoTDAClient ioTDAClient;

    /**
     * 设备表 Mapper
     */
    @Autowired
    private DeviceMapper deviceMapper;

    /**
     * 烟雾上报公共链路
     */
    @Autowired
    private SmokeReportService smokeReportService;

    /**
     * 设备健康监测服务
     */
    @Autowired
    private DeviceHealthService deviceHealthService;

    /**
     * 每台设备上一次已经成功处理的设备上报时间。
     *
     * key：数据库 device_id
     *
     * 防止 Spring Boot 多次读取
     * IoTDA 中同一条设备影子。
     */
    private final Map<Long, String> lastProcessedEventTimeByDevice =
            new ConcurrentHashMap<>();


    /**
     * ==================================================
     * 查询华为云设备真实在线状态
     * ==================================================
     *
     * 可能返回：
     *
     * ONLINE
     * OFFLINE
     * ABNORMAL
     * INACTIVE
     * FROZEN
     */
    private void checkPlatformDeviceStatus(
            Device device) {

        try {

            String cloudDeviceId =
                    device.getIotDeviceId();

            if (!StringUtils.hasText(cloudDeviceId)) {
                return;
            }

            ShowDeviceRequest request =
                    new ShowDeviceRequest();

            request.setDeviceId(cloudDeviceId);

            ShowDeviceResponse response =
                    ioTDAClient.showDevice(request);

            String jsonStr =
                    JSON.toJSONString(response);

            JSONObject json =
                    JSON.parseObject(jsonStr);

            String platformStatus =
                    json.getString("status");

            log.info(
                    "IoTDA设备状态：dbDeviceId={}，platformStatus={}",
                    device.getDeviceId(),
                    platformStatus
            );

            /*
             * 将 IoTDA 平台真实状态
             * 交给设备健康服务处理。
             */
            deviceHealthService.handlePlatformStatus(
                    device.getDeviceId(),
                    platformStatus
            );

        } catch (Exception e) {

            /*
             * ShowDevice 查询失败，
             * 不能直接说明 BearPi 已离线。
             */
            deviceHealthService.handleCommunicationFailure(
                    device.getDeviceId()
            );

            log.warn(
                    "查询IoTDA设备在线状态失败：dbDeviceId={}",
                    device.getDeviceId(),
                    e
            );
        }
    }


    /**
     * ==================================================
     * 每3秒读取一次所有真实华为云设备的设备影子
     * ==================================================
     *
     * BearPi 当前约每3秒上传一次数据。
     */
    @Scheduled(fixedRate = 3000)
    public void collectSmokeData() {

        List<Device> realDevices =
                deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                                .eq(
                                        Device::getSourceType,
                                        "REAL"
                                )
                );

        if (realDevices == null
                || realDevices.isEmpty()) {

            log.debug(
                    "数据库中没有REAL设备，采集任务跳过"
            );

            return;
        }

        /*
         * 逐台采集，单台异常不影响其他设备。
         */
        for (Device device : realDevices) {

            try {

                processDevice(device);

            } catch (Exception e) {

                String msg =
                        e.getMessage();

                /*
                 * Spring Boot 主动停止时，
                 * scheduling线程中断属于正常情况。
                 */
                if (msg != null
                        && msg.contains("interrupted")) {

                    log.info(
                            "采集任务被中断（程序停止）"
                    );

                } else {

                    /*
                     * Spring Boot -> IoTDA
                     * 或采集处理流程发生异常。
                     *
                     * consecutive_failures + 1
                     */
                    deviceHealthService
                            .handleCommunicationFailure(
                                    device.getDeviceId()
                            );

                    log.error(
                            "烟感数据采集异常：dbDeviceId={}",
                            device.getDeviceId(),
                            e
                    );
                }
            }
        }
    }


    /**
     * ==================================================
     * 采集单台真实设备
     * ==================================================
     */
    private void processDevice(Device device) {

        Long dbDeviceId =
                device.getDeviceId();

        String cloudDeviceId =
                device.getIotDeviceId();

        /*
         * REAL 设备必须绑定 iot_device_id，
         * 否则无法从 IoTDA 读取影子。
         */
        if (!StringUtils.hasText(cloudDeviceId)) {

            log.warn(
                    "REAL设备缺少iot_device_id，跳过采集：dbDeviceId={}",
                    dbDeviceId
            );

            return;
        }

        try {

            /*
             * ==========================================
             * 1. 获取华为云 IoTDA 设备影子
             * ==========================================
             */

            ShowDeviceShadowRequest request =
                    new ShowDeviceShadowRequest();

            request.setDeviceId(cloudDeviceId);

            ShowDeviceShadowResponse response =
                    ioTDAClient.showDeviceShadow(request);

            String jsonStr =
                    JSON.toJSONString(response);

            JSONObject root =
                    JSON.parseObject(jsonStr);


            /*
             * ==========================================
             * 2. 解析 shadow
             * ==========================================
             */

            JSONArray shadowList =
                    root.getJSONArray("shadow");

            if (shadowList == null
                    || shadowList.isEmpty()) {

                log.warn(
                        "设备影子为空，本次采集跳过：dbDeviceId={}",
                        dbDeviceId
                );

                return;
            }

            JSONObject shadow =
                    shadowList.getJSONObject(0);

            JSONObject reported =
                    shadow == null
                            ? null
                            : shadow.getJSONObject("reported");

            if (reported == null) {

                log.warn(
                        "设备reported数据为空，本次采集跳过：dbDeviceId={}",
                        dbDeviceId
                );

                return;
            }


            /*
             * ==========================================
             * 3. 获取 event_time
             * ==========================================
             */

            String currentEventTime =
                    reported.getString(
                            "event_time"
                    );


            /*
             * ==========================================
             * 4. 判断是不是重复设备影子
             * ==========================================
             *
             * event_time 没变化说明：
             *
             * Spring Boot 读到的是旧设备影子，
             * 并不是一条新的真实设备采样。
             *
             * 此时：
             *
             * 1. 不进入 SmokeDecisionService
             * 2. 不写 smoke_record
             * 3. 不增加连续烟雾异常次数
             * 4. 不触发 AlarmService
             *
             * 但需要：
             *
             * 1. 查询 IoTDA ONLINE / OFFLINE
             * 2. 检查数据是否已经 STALE
             */
            if (currentEventTime != null
                    && currentEventTime.equals(
                    lastProcessedEventTimeByDevice.get(
                            dbDeviceId
                    ))) {

                /*
                 * 查询 IoTDA 平台真实连接状态
                 */
                checkPlatformDeviceStatus(
                        device
                );

                /*
                 * 检查设备数据新鲜度
                 *
                 * 超过15秒没有新 event_time
                 * -> STALE
                 */
                deviceHealthService.checkStale(
                        dbDeviceId
                );

                log.debug(
                        "检测到重复设备影子，本次跳过：dbDeviceId={}，eventTime={}",
                        dbDeviceId,
                        currentEventTime
                );

                return;
            }


            /*
             * ==========================================
             * 5. 获取 reported.properties
             * ==========================================
             */

            JSONObject props =
                    reported.getJSONObject(
                            "properties"
                    );

            if (props == null) {

                log.warn(
                        "设备properties数据为空，本次采集跳过：dbDeviceId={}",
                        dbDeviceId
                );

                return;
            }


            /*
             * ==========================================
             * 6. 读取 BearPi 上传的设备属性
             * ==========================================
             */

            String smokeVal =
                    props.getString(
                            "Smoke_Value"
                    );

            String beepStatus =
                    props.getString(
                            "BeepStatus"
                    );

            String ledStatus =
                    props.getString(
                            "LedStatus"
                    );

            Double edgeBaseline =
                    props.getDouble(
                            "Baseline"
                    );

            Double smokeRatio =
                    props.getDouble(
                            "Smoke_Ratio"
                    );

            String edgeState =
                    props.getString(
                            "Smoke_State"
                    );


            if (smokeVal == null) {

                log.warn(
                        "Smoke_Value为空，本次采集跳过：dbDeviceId={}",
                        dbDeviceId
                );

                return;
            }

            if (edgeBaseline == null
                    || smokeRatio == null
                    || edgeState == null) {

                log.warn(
                        "边缘端数据不完整：dbDeviceId={}，Baseline={}，Smoke_Ratio={}，Smoke_State={}",
                        dbDeviceId,
                        edgeBaseline,
                        smokeRatio,
                        edgeState
                );
            }


            /*
             * ==========================================
             * 7. Smoke_Value 转为 double
             * ==========================================
             */

            double smokeNum;

            try {

                smokeNum =
                        Double.parseDouble(
                                smokeVal
                        );

            } catch (NumberFormatException e) {

                /*
                 * Smoke_Value 不是合法数值，
                 * 属于传感器/上报数据异常。
                 */
                log.error(
                        "Smoke_Value数值格式错误：dbDeviceId={}",
                        dbDeviceId,
                        e
                );

                return;
            }


            /*
             * ==========================================
             * 8. 送入公共上报链路
             *
             * 包括：
             * 云端智能判定（按设备独立状态）
             * 写 smoke_record
             * 告警闭环
             * 设备健康状态
             * ==========================================
             */

            smokeReportService.persistSample(
                    dbDeviceId,
                    smokeNum,
                    edgeBaseline,
                    smokeRatio,
                    edgeState
            );


            /*
             * ==========================================
             * 9. 标记本次 event_time 已经处理完成
             * ==========================================
             */

            if (currentEventTime != null) {

                lastProcessedEventTimeByDevice.put(
                        dbDeviceId,
                        currentEventTime
                );
            }

            log.info(
                    "设备上报：dbDeviceId={}，eventTime={}，烟雾={}，边缘基线={}，边缘倍率={}，边缘状态={}，LED={}，Beep={}",
                    dbDeviceId,
                    currentEventTime,
                    smokeNum,
                    edgeBaseline,
                    smokeRatio,
                    edgeState,
                    ledStatus,
                    beepStatus
            );

        } catch (Exception e) {

            String msg =
                    e.getMessage();

            if (msg != null
                    && msg.contains("interrupted")) {

                log.info(
                        "采集任务被中断（程序停止）"
                );

            } else {

                deviceHealthService.handleCommunicationFailure(
                        dbDeviceId
                );

                log.error(
                        "烟感数据采集异常：dbDeviceId={}",
                        dbDeviceId,
                        e
                );
            }
        }
    }
}
