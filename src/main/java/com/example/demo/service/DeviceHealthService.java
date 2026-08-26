package com.example.demo.service;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceHealthService {

    private static final Logger log =
            LoggerFactory.getLogger(DeviceHealthService.class);


    /**
     * BearPi 约每 3 秒上报一次。
     *
     * 超过 15 秒没有新的真实数据：
     * health_status -> STALE
     */
    private static final long STALE_SECONDS = 15;


    /**
     * Spring Boot 与 IoTDA
     * 连续通信失败阈值。
     */
    private static final int OFFLINE_FAILURE_THRESHOLD = 3;


    /**
     * SENSOR_FAULT 恢复所需要的
     * 连续正常数据次数。
     *
     * 连续 3 次检查正常以后，
     * 才允许：
     *
     * SENSOR_FAULT -> NORMAL
     */
    private static final int SENSOR_RECOVERY_REQUIRED = 3;


    /**
     * 保存每个设备连续正常数据次数。
     *
     * 当前 Demo 虽然只有一个设备，
     * 这里仍然按 deviceId 保存，
     * 后面扩展多个设备也可以继续使用。
     */
    private final Map<Long, Integer> sensorRecoveryCounts =
            new ConcurrentHashMap<>();


    @Autowired
    private DeviceMapper deviceMapper;


    /**
     * ==================================================
     * 1. 收到新的真实设备上报
     * ==================================================
     *
     * 只有 event_time 真正变化时调用。
     */
    public void handleNewReport(Long deviceId) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {

            log.warn(
                    "设备不存在，无法更新健康状态：deviceId={}",
                    deviceId
            );

            return;
        }


        /*
         * ==============================================
         * 无论传感器是否异常，
         * 有新的 event_time 都说明设备仍然在上报。
         * ==============================================
         */

        device.setLastReportTime(
                LocalDateTime.now()
        );

        device.setConsecutiveFailures(0);

        device.setStatus(1);


        /*
         * ==============================================
         * 关键修改：
         *
         * 如果当前已经是 SENSOR_FAULT，
         * 不能因为收到一条新数据
         * 就直接恢复 NORMAL。
         *
         * 是否恢复应该交给：
         *
         * checkSensorHealth()
         *
         * 连续检查3次正常后再恢复。
         * ==============================================
         */

        if (!"SENSOR_FAULT".equals(
                device.getHealthStatus())) {

            device.setHealthStatus(
                    "NORMAL"
            );
        }


        deviceMapper.updateById(
                device
        );


        log.info(
                "收到新的设备上报：deviceId={}，healthStatus={}",
                deviceId,
                device.getHealthStatus()
        );
    }


    /**
     * ==================================================
     * 2. 检查传感器 / 上报数据健康状态
     * ==================================================
     *
     * 判断：
     *
     * 设备还在上报，
     * 但是数据本身是否合理。
     */
    public void checkSensorHealth(
            Long deviceId,
            Double smokeValue,
            Double baseline,
            Double ratio,
            String edgeState) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {
            return;
        }


        /*
         * ==============================================
         * OFFLINE 优先级更高。
         * ==============================================
         */
        if ("OFFLINE".equals(
                device.getHealthStatus())) {

            return;
        }


        /*
         * ==============================================
         * 异常1：
         *
         * Smoke_Value：
         *
         * null
         * NaN
         * Infinity
         * 负数
         * ==============================================
         */
        if (smokeValue == null
                || !Double.isFinite(smokeValue)
                || smokeValue < 0) {

            markSensorFault(
                    device,
                    "Smoke_Value 非法：" + smokeValue
            );

            return;
        }


        /*
         * ==============================================
         * 异常2：
         *
         * Baseline：
         *
         * null
         * NaN
         * Infinity
         * <= 0
         * ==============================================
         */
        if (baseline == null
                || !Double.isFinite(baseline)
                || baseline <= 0) {

            markSensorFault(
                    device,
                    "Baseline 非法：" + baseline
            );

            return;
        }


        /*
         * ==============================================
         * 异常3：
         *
         * Smoke_Ratio：
         *
         * null
         * NaN
         * Infinity
         * 负数
         * ==============================================
         */
        if (ratio == null
                || !Double.isFinite(ratio)
                || ratio < 0) {

            markSensorFault(
                    device,
                    "Smoke_Ratio 非法：" + ratio
            );

            return;
        }


        /*
         * ==============================================
         * 异常4：
         *
         * 云端重新计算：
         *
         * expectedRatio =
         * smokeValue / baseline
         *
         * 与 BearPi 上报的 ratio 进行比较。
         * ==============================================
         */

        double expectedRatio =
                smokeValue / baseline;

        double difference =
                Math.abs(
                        expectedRatio - ratio
                );


        /*
         * 允许一定浮点误差和采样误差。
         *
         * 容差取：
         *
         * 0.15
         *
         * 和
         *
         * expectedRatio 的20%
         *
         * 两者中较大的一个。
         */
        double tolerance =
                Math.max(
                        0.15,
                        Math.abs(expectedRatio) * 0.20
                );


        if (difference > tolerance) {

            markSensorFault(
                    device,
                    "Smoke_Ratio 与 Smoke_Value/Baseline 不一致"
                            + "，云端计算="
                            + expectedRatio
                            + "，边缘上报="
                            + ratio
            );

            return;
        }


        /*
         * ==============================================
         * 异常5：
         *
         * BearPi Smoke_State 必须属于：
         *
         * NORMAL
         * PREWARNING
         * ALARM
         * ==============================================
         */
        if (edgeState == null
                || !(
                "NORMAL".equals(edgeState)
                        || "PREWARNING".equals(edgeState)
                        || "ALARM".equals(edgeState)
        )) {

            markSensorFault(
                    device,
                    "Smoke_State 非法：" + edgeState
            );

            return;
        }


        /*
         * ==============================================
         * 到这里：
         *
         * 本次传感器数据全部检查通过。
         * ==============================================
         */


        /*
         * 如果当前处于 SENSOR_FAULT，
         * 不能立即恢复。
         */
        if ("SENSOR_FAULT".equals(
                device.getHealthStatus())) {


            /*
             * 当前连续正常次数 + 1
             */
            int normalCount =
                    sensorRecoveryCounts
                            .getOrDefault(
                                    deviceId,
                                    0
                            ) + 1;


            sensorRecoveryCounts.put(
                    deviceId,
                    normalCount
            );


            /*
             * ==========================================
             * 连续正常次数不足3次：
             *
             * 继续保持 SENSOR_FAULT。
             * ==========================================
             */
            if (normalCount
                    < SENSOR_RECOVERY_REQUIRED) {

                log.warn(
                        "传感器正在恢复：deviceId={}，连续正常={}/{}，healthStatus仍为SENSOR_FAULT",
                        deviceId,
                        normalCount,
                        SENSOR_RECOVERY_REQUIRED
                );

                return;
            }


            /*
             * ==========================================
             * 连续3次检查正常：
             *
             * SENSOR_FAULT -> NORMAL
             * ==========================================
             */

            device.setHealthStatus(
                    "NORMAL"
            );

            deviceMapper.updateById(
                    device
            );


            /*
             * 恢复完成，
             * 清空计数。
             */
            sensorRecoveryCounts.remove(
                    deviceId
            );


            log.info(
                    "传感器连续{}次数据正常，故障解除：deviceId={}，healthStatus=NORMAL",
                    SENSOR_RECOVERY_REQUIRED,
                    deviceId
            );


            return;
        }


        /*
         * 当前原本就是正常状态。
         *
         * 确保不存在残留恢复计数。
         */
        sensorRecoveryCounts.remove(
                deviceId
        );
    }


    /**
     * ==================================================
     * 3. 标记传感器异常
     * ==================================================
     */
    private void markSensorFault(
            Device device,
            String reason) {

        Long deviceId =
                device.getDeviceId();


        /*
         * ==============================================
         * 一旦再次发现异常，
         * 连续恢复计数立即清零。
         *
         * 例如：
         *
         * 异常
         * ↓
         * 正常1次
         * ↓
         * 正常2次
         * ↓
         * 又异常
         *
         * 那么必须重新从0开始恢复。
         * ==============================================
         */
        sensorRecoveryCounts.remove(
                deviceId
        );


        if (!"SENSOR_FAULT".equals(
                device.getHealthStatus())) {

            device.setHealthStatus(
                    "SENSOR_FAULT"
            );

            deviceMapper.updateById(
                    device
            );
        }


        log.warn(
                "检测到传感器数据异常：deviceId={}，healthStatus=SENSOR_FAULT，原因={}",
                deviceId,
                reason
        );
    }


    /**
     * ==================================================
     * 4. 检查数据是否过期
     * ==================================================
     *
     * 超过15秒没有新的真实 event_time：
     *
     * STALE
     */
    public void checkStale(Long deviceId) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {
            return;
        }


        /*
         * IoTDA 已经明确 OFFLINE 时，
         * 不允许再覆盖成 STALE。
         */
        if ("OFFLINE".equals(
                device.getHealthStatus())) {

            return;
        }


        LocalDateTime lastReportTime =
                device.getLastReportTime();


        /*
         * Spring Boot 启动后，
         * 从来没有收到过新的真实数据。
         */
        if (lastReportTime == null) {

            device.setHealthStatus(
                    "STALE"
            );

            deviceMapper.updateById(
                    device
            );

            log.warn(
                    "设备尚无有效上报记录：deviceId={}",
                    deviceId
            );

            return;
        }


        long seconds =
                Duration.between(
                        lastReportTime,
                        LocalDateTime.now()
                ).getSeconds();


        /*
         * ==============================================
         * 超过15秒没有新数据。
         * ==============================================
         */
        if (seconds >= STALE_SECONDS) {

            if (!"STALE".equals(
                    device.getHealthStatus())) {

                device.setHealthStatus(
                        "STALE"
                );

                deviceMapper.updateById(
                        device
                );

                /*
                 * 数据都已经停止更新，
                 * 之前的 SENSOR_FAULT 恢复计数
                 * 也没有意义了。
                 */
                sensorRecoveryCounts.remove(
                        deviceId
                );


                log.warn(
                        "设备数据已过期：deviceId={}，{}秒没有新上报，healthStatus=STALE",
                        deviceId,
                        seconds
                );
            }
        }
    }


    /**
     * ==================================================
     * 5. 处理 IoTDA 平台在线状态
     * ==================================================
     */
    public void handlePlatformStatus(
            Long deviceId,
            String platformStatus) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {

            log.warn(
                    "设备不存在，无法处理平台状态：deviceId={}",
                    deviceId
            );

            return;
        }


        if (platformStatus == null) {

            log.warn(
                    "华为云设备状态为空：deviceId={}",
                    deviceId
            );

            return;
        }


        /*
         * ==============================================
         * IoTDA -> OFFLINE
         * ==============================================
         */
        if ("OFFLINE".equalsIgnoreCase(
                platformStatus)) {

            device.setStatus(
                    0
            );

            device.setHealthStatus(
                    "OFFLINE"
            );

            deviceMapper.updateById(
                    device
            );


            /*
             * 设备已经离线，
             * SENSOR_FAULT恢复计数清除。
             */
            sensorRecoveryCounts.remove(
                    deviceId
            );


            log.warn(
                    "华为云确认设备离线：deviceId={}，healthStatus=OFFLINE",
                    deviceId
            );

            return;
        }


        /*
         * ==============================================
         * IoTDA -> ONLINE
         * ==============================================
         */
        if ("ONLINE".equalsIgnoreCase(
                platformStatus)) {

            device.setStatus(
                    1
            );


            /*
             * ONLINE 只能证明：
             *
             * BearPi 已经与 IoTDA 建立连接。
             *
             * 不代表：
             *
             * 数据一定正常。
             *
             * 所以不能直接：
             *
             * healthStatus = NORMAL
             */


            /*
             * 从 OFFLINE 恢复到 ONLINE 时：
             *
             * 暂时进入 STALE。
             *
             * 等待新的 event_time。
             */
            if ("OFFLINE".equals(
                    device.getHealthStatus())) {

                device.setHealthStatus(
                        "STALE"
                );
            }


            deviceMapper.updateById(
                    device
            );


            log.debug(
                    "华为云设备在线：deviceId={}，platformStatus=ONLINE，healthStatus={}",
                    deviceId,
                    device.getHealthStatus()
            );

            return;
        }


        /*
         * ==============================================
         * 其他 IoTDA 状态
         * ==============================================
         */
        log.warn(
                "检测到其他华为云设备状态：deviceId={}，platformStatus={}",
                deviceId,
                platformStatus
        );
    }


    /**
     * ==================================================
     * 6. Spring Boot 与 IoTDA 通信异常
     * ==================================================
     */
    public void handleCommunicationFailure(
            Long deviceId) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {
            return;
        }


        Integer failures =
                device.getConsecutiveFailures();


        if (failures == null) {
            failures = 0;
        }


        failures++;


        device.setConsecutiveFailures(
                failures
        );


        deviceMapper.updateById(
                device
        );


        /*
         * Spring Boot 连不上 IoTDA
         * 不能直接证明 BearPi 离线。
         */
        if (failures
                >= OFFLINE_FAILURE_THRESHOLD) {

            log.warn(
                    "Spring Boot 与 IoTDA 连续通信失败：deviceId={}，failureCount={}",
                    deviceId,
                    failures
            );
        }
    }
}