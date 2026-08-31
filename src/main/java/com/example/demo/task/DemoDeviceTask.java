package com.example.demo.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.service.DemoDeviceService;
import com.example.demo.service.DemoOutput;
import com.example.demo.service.DeviceHealthService;
import com.example.demo.service.SmokeReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEMO 模拟寝室设备采集任务。
 *
 * 每 3 秒遍历数据库中的 DEMO 设备，
 * 由 DemoDeviceService 生成模拟采样，
 * 并送入与真实设备完全相同的 SmokeReportService 链路。
 *
 * 采样频率分级：
 * - 普通（NORMAL）设备每 20 个周期持久化一次（约 60 秒），
 *   进一步降低数据库写入与告警判定的压力；
 * - 异常演示设备（WARNING/ALARM/OFFLINE/STALE 等）
 *   保持每 3 秒高频持久化，保证异常能被及时判定。
 */
@Component
public class DemoDeviceTask {

    private static final Logger log =
            LoggerFactory.getLogger(DemoDeviceTask.class);

    /**
     * 普通 NORMAL 设备每多少个采集周期持久化一次。
     * 周期 3 秒 × 20 = 60 秒。
     */
    private static final int NORMAL_PERSIST_INTERVAL = 20;

    /**
     * 每台 DEMO 设备的 NORMAL 累计周期数，
     * 用于普通设备低频持久化。
     */
    private final Map<Long, Integer> normalTicks =
            new ConcurrentHashMap<>();

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DemoDeviceService demoDeviceService;

    @Autowired
    private SmokeReportService smokeReportService;

    @Autowired
    private DeviceHealthService deviceHealthService;


    @Scheduled(fixedRate = 3000)
    public void runDemoDevices() {

        List<Device> demoDevices =
                deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                                .eq(
                                        Device::getSourceType,
                                        "DEMO"
                                )
                );

        for (Device device : demoDevices) {

            Long deviceId =
                    device.getDeviceId();

            try {

                String scenario =
                        demoDeviceService.getScenario(
                                deviceId
                        );

                /*
                 * 采样频率分级：
                 * 普通 NORMAL 设备只按 NORMAL_PERSIST_INTERVAL 周期
                 * 持久化一次；异常演示设备每周期高频持久化。
                 * 低频周期内跳过 nextSample 与健康状态处理，
                 * 避免普通设备被误判为过期/离线。
                 */
                if (DemoDeviceService.SCENARIO_NORMAL
                        .equals(scenario)) {

                    int tick =
                            normalTicks.merge(
                                    deviceId,
                                    1,
                                    Integer::sum
                            );

                    if (tick % NORMAL_PERSIST_INTERVAL != 0) {
                        continue;
                    }
                }

                DemoOutput output =
                        demoDeviceService.nextSample(
                                deviceId
                        );

                /*
                 * 不上报数据的场景：
                 * - OFFLINE：设备离线
                 * - STALE：数据过期（未被判定离线）
                 * 二者分开处理，避免 STALE 被误标为 OFFLINE。
                 */
                if (output == null) {

                    if (DemoDeviceService.SCENARIO_STALE
                            .equals(scenario)) {

                        deviceHealthService.markStale(
                                deviceId
                        );

                    } else {

                        deviceHealthService.handlePlatformStatus(
                                deviceId,
                                "OFFLINE"
                        );
                    }

                    continue;
                }

                /*
                 * 与真实设备完全相同的链路：
                 * SmokeDecisionService → smoke_record → alarm
                 */
                smokeReportService.persistSample(
                        deviceId,
                        output.smokeValue(),
                        output.edgeBaseline(),
                        output.edgeRatio(),
                        output.edgeState()
                );

            } catch (Exception e) {

                /*
                 * 单台 DEMO 设备异常不影响其他设备。
                 */
                log.error(
                        "DEMO设备模拟采集异常：deviceId={}",
                        deviceId,
                        e
                );
            }
        }
    }
}
