package com.example.demo.service;

import com.example.demo.vo.SmokeDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SmokeDecisionService 并发安全测试。
 *
 * analyze() 只对单台设备上下文加锁：
 * 不同设备并行、同一设备串行。
 *
 * 这里用多线程验证：
 * 1. 多台设备并行分析互不污染；
 * 2. 同一设备并发喂数据不会破坏滑动窗口 / 基线。
 */
public class SmokeDecisionConcurrencyTest {

    /**
     * 8 台设备各自在独立线程中
     * 走相同的报警序列（5×20 后 3×60）。
     *
     * 若全局锁或状态互相污染，
     * 其中某些设备可能到不了 ALARM。
     */
    @Test
    void parallelDevicesReachAlarmIndependently() throws Exception {

        SmokeDecisionService service =
                new SmokeDecisionService();

        ExecutorService pool =
                Executors.newFixedThreadPool(8);

        try {
            List<Future<String>> futures =
                    new ArrayList<>();

            for (int d = 1; d <= 8; d++) {

                long deviceId = 1000L + d;

                futures.add(
                        pool.submit(() -> {
                            for (int i = 0; i < 5; i++) {
                                service.analyze(deviceId, 20);
                            }

                            String state = null;
                            for (int i = 0; i < 3; i++) {
                                state = service.analyze(
                                        deviceId,
                                        60
                                ).getState();
                            }
                            return state;
                        })
                );
            }

            for (int d = 1; d <= 8; d++) {

                String state = futures.get(d - 1).get();

                assertEquals(
                        "ALARM",
                        state,
                        "设备 1000+" + d + " 应独立进入 ALARM"
                );
            }

        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 多线程对同一设备同时喂正常值 20。
     *
     * 若缺少 per-context 锁，
     * ArrayDeque 并发修改可能抛异常
     * 或破坏滑动窗口 / 基线。
     *
     * 加锁后：
     * 窗口始终是 5 个 20，
     * 状态保持 NORMAL，风险分为 0。
     */
    @Test
    void sameDeviceConcurrentSamplesStayConsistent() throws Exception {

        SmokeDecisionService service =
                new SmokeDecisionService();

        ExecutorService pool =
                Executors.newFixedThreadPool(4);

        try {
            List<Future<?>> futures =
                    new ArrayList<>();

            for (int t = 0; t < 4; t++) {

                futures.add(
                        pool.submit(() -> {
                            for (int i = 0; i < 25; i++) {
                                service.analyze(101L, 20);
                            }
                        })
                );
            }

            for (Future<?> f : futures) {
                f.get(); // 线程内异常会在这里抛出
            }

            SmokeDecision result =
                    service.analyze(101L, 20);

            assertEquals(
                    "NORMAL",
                    result.getState()
            );

            assertEquals(
                    0,
                    result.getRiskScore()
            );

        } finally {
            pool.shutdownNow();
        }
    }
}
