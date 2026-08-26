package com.example.demo.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.service.AlarmService;
import com.example.demo.service.DeviceHealthService;
import com.example.demo.service.SmokeDecisionService;
import com.example.demo.vo.SmokeDecision;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
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

    private static final Logger log =
            LoggerFactory.getLogger(SmokeCollectTask.class);

    /**
     * 华为云 IoTDA 客户端
     */
    @Autowired
    private IoTDAClient ioTDAClient;

    /**
     * 烟雾历史数据 Mapper
     */
    @Autowired
    private SmokeRecordMapper smokeRecordMapper;

    /**
     * 云端烟雾智能判定服务
     */
    @Autowired
    private SmokeDecisionService smokeDecisionService;

    /**
     * 告警事件处理服务
     */
    @Autowired
    private AlarmService alarmService;

    /**
     * 设备健康监测服务
     */
    @Autowired
    private DeviceHealthService deviceHealthService;

    /**
     * 华为云设备 ID
     */
    @Value("${huawei.iot.deviceId}")
    private String deviceId;

    /**
     * 上一次已经成功处理的设备上报时间。
     *
     * 防止 Spring Boot 多次读取
     * IoTDA 中同一条设备影子。
     */
    private String lastProcessedEventTime = null;


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
    private void checkPlatformDeviceStatus() {

        try {

            ShowDeviceRequest request =
                    new ShowDeviceRequest();

            request.setDeviceId(deviceId);

            ShowDeviceResponse response =
                    ioTDAClient.showDevice(request);

            String jsonStr =
                    JSON.toJSONString(response);

            JSONObject json =
                    JSON.parseObject(jsonStr);

            String platformStatus =
                    json.getString("status");

            log.info(
                    "IoTDA设备状态：{}",
                    platformStatus
            );

            /*
             * 将 IoTDA 平台真实状态
             * 交给设备健康服务处理。
             */
            deviceHealthService.handlePlatformStatus(
                    1L,
                    platformStatus
            );

        } catch (Exception e) {

            /*
             * ShowDevice 查询失败。
             *
             * 这里只说明：
             * Spring Boot -> IoTDA
             * 的通信或接口调用出现异常。
             *
             * 不能直接说明 BearPi 已离线。
             */
            deviceHealthService
                    .handleCommunicationFailure(
                            1L
                    );

            log.warn(
                    "查询IoTDA设备在线状态失败",
                    e
            );
        }
    }


    /**
     * ==================================================
     * 每3秒读取一次华为云设备影子
     * ==================================================
     *
     * BearPi 当前约每3秒上传一次数据。
     */
    @Scheduled(fixedRate = 3000)
    public void collectSmokeData() {

        try {

            /*
             * ==========================================
             * 1. 获取华为云 IoTDA 设备影子
             * ==========================================
             */

            ShowDeviceShadowRequest request =
                    new ShowDeviceShadowRequest();

            request.setDeviceId(deviceId);

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
                        "设备影子为空，本次采集跳过"
                );

                return;
            }

            JSONObject shadow =
                    shadowList.getJSONObject(0);

            if (shadow == null) {

                log.warn(
                        "设备影子内容为空，本次采集跳过"
                );

                return;
            }

            JSONObject reported =
                    shadow.getJSONObject("reported");

            if (reported == null) {

                log.warn(
                        "设备reported数据为空，本次采集跳过"
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
                    lastProcessedEventTime)) {

                /*
                 * 查询 IoTDA 平台真实连接状态
                 */
                checkPlatformDeviceStatus();

                /*
                 * 检查设备数据新鲜度
                 *
                 * 超过15秒没有新 event_time
                 * -> STALE
                 */
                deviceHealthService.checkStale(
                        1L
                );

                log.debug(
                        "检测到重复设备影子，本次跳过：eventTime={}",
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
                        "设备properties数据为空，本次采集跳过"
                );

                return;
            }


            /*
             * ==========================================
             * 6. 读取 BearPi 上传的设备属性
             * ==========================================
             */

            /*
             * 当前烟雾浓度
             */
            String smokeVal =
                    props.getString(
                            "Smoke_Value"
                    );

            /*
             * 蜂鸣器状态
             */
            String beepStatus =
                    props.getString(
                            "BeepStatus"
                    );

            /*
             * LED状态
             */
            String ledStatus =
                    props.getString(
                            "LedStatus"
                    );

            /*
             * BearPi动态环境基线
             */
            Double edgeBaseline =
                    props.getDouble(
                            "Baseline"
                    );

            /*
             * 当前烟雾 / 动态基线倍率
             */
            Double smokeRatio =
                    props.getDouble(
                            "Smoke_Ratio"
                    );

            /*
             * BearPi边缘判定：
             *
             * NORMAL
             * PREWARNING
             * ALARM
             */
            String edgeState =
                    props.getString(
                            "Smoke_State"
                    );


            /*
             * ==========================================
             * 7. 基础数据有效性检查
             * ==========================================
             */

            if (smokeVal == null) {

                log.warn(
                        "Smoke_Value为空，本次采集跳过"
                );

                return;
            }


            /*
             * 边缘端附加属性如果缺失，
             * 先记录警告。
             *
             * 后面的 checkSensorHealth()
             * 会进一步将这种情况识别为
             * SENSOR_FAULT。
             */
            if (edgeBaseline == null
                    || smokeRatio == null
                    || edgeState == null) {

                log.warn(
                        "边缘端数据不完整：Baseline={}，Smoke_Ratio={}，Smoke_State={}",
                        edgeBaseline,
                        smokeRatio,
                        edgeState
                );
            }


            /*
             * Smoke_Value 转为 double。
             */
            double smokeNum =
                    Double.parseDouble(
                            smokeVal
                    );


            /*
             * ==========================================
             * 8. Spring Boot 云端智能烟雾判定
             * ==========================================
             *
             * 包括：
             *
             * 稳定背景基线
             * 滑动窗口
             * 连续异常
             * 上升趋势
             * 单点尖峰抑制
             * ALARM锁存
             * 连续恢复
             */
            SmokeDecision decision =
                    smokeDecisionService.analyze(
                            smokeNum
                    );


            /*
             * ==========================================
             * 9. 构造 smoke_record
             * ==========================================
             */

            SmokeRecord record =
                    new SmokeRecord();

            /*
             * 当前 Demo 中：
             * BearPi 对应数据库 device_id = 1
             */
            record.setDeviceId(
                    1L
            );

            /*
             * 当前 Spring Boot
             * 收到并处理数据的时间。
             */
            record.setCollectTime(
                    LocalDateTime.now()
            );

            /*
             * 原始烟雾浓度
             */
            record.setSmokeConcentration(
                    smokeNum
            );


            /*
             * ==========================================
             * 10. 保存 BearPi 边缘计算结果
             * ==========================================
             */

            record.setEdgeBaseline(
                    edgeBaseline
            );

            record.setSmokeRatio(
                    smokeRatio
            );

            record.setEdgeState(
                    edgeState
            );


            /*
             * ==========================================
             * 11. 保存 Spring Boot 云端结果
             * ==========================================
             */

            record.setCloudState(
                    decision.getState()
            );

            record.setRiskScore(
                    decision.getRiskScore()
            );

            record.setDecisionReason(
                    decision.getReason()
            );


            /*
             * ==========================================
             * 12. 兼容 smoke_record 原来的 alarm 字段
             * ==========================================
             *
             * 云端正式 ALARM -> 1
             * 其他状态        -> 0
             */
            record.setAlarm(
                    "ALARM".equals(
                            decision.getState()
                    ) ? 1 : 0
            );


            /*
             * ==========================================
             * 13. 写入 MySQL
             * ==========================================
             */

            smokeRecordMapper.insert(
                    record
            );


            /*
             * ==========================================
             * 14. 正式告警事件闭环
             * ==========================================
             *
             * 首次 ALARM：
             * -> 插入 alarm
             *
             * 持续 ALARM：
             * -> 不重复插入
             *
             * 恢复 NORMAL：
             * -> 填写 recover_time
             */
            alarmService.handleSmokeDecision(
                    1L,
                    decision.getState(),
                    decision.getReason()
            );


            /*
             * ==========================================
             * 15. 新真实数据 -> 基础健康状态 NORMAL
             * ==========================================
             *
             * 新 event_time 说明：
             *
             * BearPi
             *   ↓
             * IoTDA
             *   ↓
             * Spring Boot
             *
             * 整条数据链当前能够正常工作。
             *
             * 所以先设置：
             *
             * health_status = NORMAL
             * status = 1
             * last_report_time = 当前时间
             * consecutive_failures = 0
             */
            deviceHealthService.handleNewReport(
                    1L
            );


            /*
             * ==========================================
             * 16. SENSOR_FAULT 传感器健康检查
             * ==========================================
             *
             * 这里必须放在 handleNewReport() 后面。
             *
             * 因为 handleNewReport()
             * 会先确认：
             *
             * “设备确实产生了新的数据”
             *
             * 然后这里进一步确认：
             *
             * “这些数据本身是否合理”
             *
             * 检查内容：
             *
             * 1. Smoke_Value是否合法
             * 2. Baseline是否合法
             * 3. Smoke_Ratio是否合法
             * 4. ratio 与 smoke / baseline 是否一致
             * 5. Smoke_State是否合法
             *
             * 如果异常：
             *
             * health_status = SENSOR_FAULT
             */
            deviceHealthService.checkSensorHealth(
                    1L,
                    smokeNum,
                    edgeBaseline,
                    smokeRatio,
                    edgeState
            );


            /*
             * ==========================================
             * 17. 标记本次 event_time 已经处理完成
             * ==========================================
             *
             * 放在数据库、烟雾告警、
             * 设备健康检查都执行完成以后。
             */
            if (currentEventTime != null) {

                lastProcessedEventTime =
                        currentEventTime;
            }


            /*
             * ==========================================
             * 18. 控制台日志
             * ==========================================
             */
            log.info(
                    "设备上报时间={} | 烟雾={} | 边缘基线={} | 边缘倍率={} | 边缘状态={} | LED={} | Beep={} | 云端状态={} | 风险评分={} | 原因={}",
                    currentEventTime,
                    smokeNum,
                    edgeBaseline,
                    smokeRatio,
                    edgeState,
                    ledStatus,
                    beepStatus,
                    decision.getState(),
                    decision.getRiskScore(),
                    decision.getReason()
            );

        }

        /*
         * ==============================================
         * Smoke_Value 格式错误
         * ==============================================
         */
        catch (NumberFormatException e) {

            /*
             * 当前 Smoke_Value 不是合法数值。
             *
             * 这本身就是传感器/上报数据异常。
             *
             * 目前先通过日志记录。
             *
             * 后面如果需要进一步完善，
             * 可以再单独增加一个
             * handleSensorFormatFault() 方法。
             */
            log.error(
                    "Smoke_Value数值格式错误",
                    e
            );
        }

        /*
         * ==============================================
         * 其他采集异常
         * ==============================================
         */
        catch (Exception e) {

            String msg =
                    e.getMessage();


            /*
             * Spring Boot 主动停止时，
             * scheduling线程中断属于正常情况。
             */
            if (msg != null
                    && msg.contains(
                    "interrupted")) {

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
                                1L
                        );

                log.error(
                        "烟感数据采集异常",
                        e
                );
            }
        }
    }
}