package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 环境监测记录（DEMO 模拟数据）。
 *
 * 记录各监测片区的温湿度 / 土壤湿度 / 风速 / 降雨量，
 * 作为火险气象评分的输入。仅用于演示，
 * 数据来源为 ForestDatabaseInitializer 播种的 DEMO 环境记录，
 * 不涉及真实气象站联网。
 */
@Data
@TableName("environment_record")
public class EnvironmentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属区域ID（forest_zone.id） */
    private Long zoneId;

    /** 所属区域名称 */
    private String zoneName;

    /** 记录时间 */
    private LocalDateTime recordTime;

    /** 温度（摄氏度） */
    private BigDecimal temperature;

    /** 相对湿度（%） */
    private BigDecimal humidity;

    /** 土壤湿度（%） */
    private BigDecimal soilMoisture;

    /** 风速（米/秒） */
    private BigDecimal windSpeed;

    /** 降雨量（毫米） */
    private BigDecimal rainfallMm;
}
