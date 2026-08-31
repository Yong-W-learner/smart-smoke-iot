package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.mapper.EnvironmentRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 火险气象评分（0~100，越高越利于火险发生）。
 *
 * 透明规则，非AI：
 * 取指定分区最近一条环境监测记录，
 * 温度 / 湿度 / 土壤湿度分别映射到 0~100 分，
 * 再按权重 0.30 / 0.30 / 0.40 加权求和。
 *
 * 权重已按冻结规格修正：温度贡献 0~30、湿度贡献 0~30、
 * 土壤湿度贡献 0~40，三项相加 0~100。
 *
 * 无环境数据时返回中性默认值（40），不因缺数据误判高火险。
 */
@Service
public class FireWeatherService {

    public static final int DEFAULT_WEATHER_SCORE = 40;

    private static final double TEMP_WEIGHT = 0.30;
    private static final double HUMIDITY_WEIGHT = 0.30;
    private static final double SOIL_WEIGHT = 0.40;

    private final EnvironmentRecordMapper environmentRecordMapper;

    public FireWeatherService(EnvironmentRecordMapper environmentRecordMapper) {
        this.environmentRecordMapper = environmentRecordMapper;
    }

    /**
     * 指定分区的火险气象评分（0~100）。
     */
    public int score(Long zoneId) {

        if (zoneId == null) {
            return DEFAULT_WEATHER_SCORE;
        }

        EnvironmentRecord latest = latestRecord(zoneId);

        if (latest == null) {
            return DEFAULT_WEATHER_SCORE;
        }

        double temp = temperatureScore(latest.getTemperature());
        double humidity = humidityScore(latest.getHumidity());
        double soil = soilMoistureScore(latest.getSoilMoisture());

        double combined = temp * TEMP_WEIGHT
                + humidity * HUMIDITY_WEIGHT
                + soil * SOIL_WEIGHT;

        return (int) Math.round(clamp(combined, 0, 100));
    }

    /**
     * 温度评分：越高越利于火险（≤15℃→20，≥40℃→95，线性）。
     */
    static double temperatureScore(BigDecimal temperature) {

        if (temperature == null) {
            return 50;
        }

        double t = temperature.doubleValue();

        if (t <= 15) return 20;
        if (t >= 40) return 95;
        return 20 + (t - 15) / 25.0 * 75;
    }

    /**
     * 相对湿度评分：越低越利于火险（≥80%→10，≤20%→95，线性）。
     */
    static double humidityScore(BigDecimal humidity) {

        if (humidity == null) {
            return 50;
        }

        double h = humidity.doubleValue();

        if (h >= 80) return 10;
        if (h <= 20) return 95;
        return 95 - (h - 20) / 60.0 * 85;
    }

    /**
     * 土壤湿度评分：越低越利于火险（≥60%→15，≤20%→90，线性）。
     */
    static double soilMoistureScore(BigDecimal soilMoisture) {

        if (soilMoisture == null) {
            return 50;
        }

        double s = soilMoisture.doubleValue();

        if (s >= 60) return 15;
        if (s <= 20) return 90;
        return 90 - (s - 20) / 40.0 * 75;
    }

    private EnvironmentRecord latestRecord(Long zoneId) {

        return environmentRecordMapper.selectOne(
                new LambdaQueryWrapper<EnvironmentRecord>()
                        .eq(EnvironmentRecord::getZoneId, zoneId)
                        .orderByDesc(EnvironmentRecord::getRecordTime)
                        .last("LIMIT 1")
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
