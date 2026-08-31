package com.example.demo.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.entity.ForestZone;
import com.example.demo.mapper.EnvironmentRecordMapper;
import com.example.demo.service.ForestZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * DEMO 环境气象任务。
 *
 * 每 30 秒为每个森林分区生成一条新的环境监测记录
 * （温度 / 相对湿度 / 土壤湿度 / 风速 / 降雨量），
 * 以最近一条记录为基准做小幅随机游走。
 *
 * 数据持续写入 environment_record，
 * 驱动 FireWeatherService 的火险气象评分实时刷新，
 * 指挥台与巡护端的气象卡片因此能看到"存活"的动态数据。
 *
 * 全部为规则生成的 DEMO 数据，不涉及真实气象站联网。
 */
@Component
public class DemoEnvironmentTask {

    private static final Logger log =
            LoggerFactory.getLogger(DemoEnvironmentTask.class);

    /**
     * 各气象指标的取值范围（随机游走边界）。
     */
    private static final double TEMP_MIN = 15.0;
    private static final double TEMP_MAX = 40.0;
    private static final double HUMIDITY_MIN = 20.0;
    private static final double HUMIDITY_MAX = 90.0;
    private static final double SOIL_MIN = 10.0;
    private static final double SOIL_MAX = 80.0;
    private static final double WIND_MIN = 0.0;
    private static final double WIND_MAX = 12.0;
    private static final double RAIN_MIN = 0.0;
    private static final double RAIN_MAX = 10.0;

    @Autowired
    private ForestZoneService forestZoneService;

    @Autowired
    private EnvironmentRecordMapper environmentRecordMapper;

    private final Random random = new Random();

    @Scheduled(fixedRate = 30000)
    public void runEnvironmentDemo() {

        List<ForestZone> zones =
                forestZoneService.listZones();

        for (ForestZone zone : zones) {

            try {

                EnvironmentRecord latest =
                        latestRecord(zone.getId());

                EnvironmentRecord record =
                        new EnvironmentRecord();

                record.setZoneId(zone.getId());
                record.setZoneName(zone.getZoneName());
                record.setRecordTime(LocalDateTime.now());
                record.setTemperature(walk(
                        latest == null ? null : latest.getTemperature(),
                        26.0, TEMP_MIN, TEMP_MAX, 0.8));
                record.setHumidity(walk(
                        latest == null ? null : latest.getHumidity(),
                        48.0, HUMIDITY_MIN, HUMIDITY_MAX, 1.5));
                record.setSoilMoisture(walk(
                        latest == null ? null : latest.getSoilMoisture(),
                        40.0, SOIL_MIN, SOIL_MAX, 1.0));
                record.setWindSpeed(walk(
                        latest == null ? null : latest.getWindSpeed(),
                        3.0, WIND_MIN, WIND_MAX, 0.6));
                record.setRainfallMm(walk(
                        latest == null ? null : latest.getRainfallMm(),
                        0.2, RAIN_MIN, RAIN_MAX, 0.3));

                environmentRecordMapper.insert(record);

            } catch (Exception e) {

                /*
                 * 单个分区气象生成失败不影响其他分区。
                 */
                log.error(
                        "DEMO环境气象生成异常：zoneId={}",
                        zone.getId(),
                        e
                );
            }
        }
    }

    /**
     * 以当前值（或默认基准）做小幅随机游走，并钳制在合法范围内。
     */
    private BigDecimal walk(
            BigDecimal current,
            double defaultValue,
            double min,
            double max,
            double step) {

        double base =
                current == null
                        ? defaultValue
                        : current.doubleValue();

        double next =
                Math.max(min, Math.min(max, base
                        + (random.nextDouble() - 0.5) * step));

        return BigDecimal.valueOf(next)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 指定分区最近一条环境记录。
     */
    private EnvironmentRecord latestRecord(Long zoneId) {

        return environmentRecordMapper.selectOne(
                new LambdaQueryWrapper<EnvironmentRecord>()
                        .eq(EnvironmentRecord::getZoneId, zoneId)
                        .orderByDesc(EnvironmentRecord::getRecordTime)
                        .last("LIMIT 1")
        );
    }
}
