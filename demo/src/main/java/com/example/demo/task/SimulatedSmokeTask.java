package com.example.demo.task;

import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟设备数据生成任务。
 *
 * 背景：真实硬件只有一台（device_id=1，由 SmokeCollectTask 实时采集华为云影子）。
 * 为了让「多住户」演示成立——每个居民注册后绑定的传感器都能看到实时数据，
 * 这里对非真机（device_id != 1 且 status=1 在线）的模拟设备生成平滑随机烟雾浓度。
 * 数据保持在正常室内范围（5~45ppm），不触发告警；真实告警仍以真机为准。
 */
@Component
public class SimulatedSmokeTask {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmokeTask.class);

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private SmokeRecordMapper smokeRecordMapper;

    // 每台模拟设备的当前浓度（用于平滑随机游走，避免跳变）
    private final Map<Long, Double> current = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Scheduled(fixedRate = 2000)
    public void simulate() {
        try {
            List<Device> devices = deviceMapper.selectList(null);
            for (Device d : devices) {
                Long id = d.getDeviceId();
                // 真机由 SmokeCollectTask 采集，跳过
                if (id == null || id == 1L) continue;
                // 仅在线设备生成数据
                if (d.getStatus() == null || d.getStatus() != 1) continue;

                double prev = current.getOrDefault(id, 18.0);
                // 平滑随机游走：每次 -2 ~ +2，限制在 [5, 45]，模拟室内正常烟雾浓度
                double next = prev + (random.nextDouble() * 4 - 2);
                if (next < 5) next = 5;
                if (next > 45) next = 45;
                current.put(id, next);

                SmokeRecord r = new SmokeRecord();
                r.setDeviceId(id);
                r.setCollectTime(LocalDateTime.now());
                r.setSmokeConcentration(Math.round(next * 10) / 10.0);
                r.setAlarm(0);
                smokeRecordMapper.insert(r);
            }
        } catch (Exception e) {
            log.warn("模拟设备数据生成异常：{}", e.getMessage());
        }
    }
}
