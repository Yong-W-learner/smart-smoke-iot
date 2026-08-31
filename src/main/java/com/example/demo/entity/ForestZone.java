package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 森林防火分区。
 *
 * 森林公园划分为 Z01~Z04 四个监测片区，
 * 每个片区承载一批监测节点 / 古树 / 野生动物栖息地，
 * 并作为巡护员数据权限的最小授权单位。
 */
@Data
@TableName("forest_zone")
public class ForestZone {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 区域编号：Z01 / Z02 / Z03 / Z04 */
    private String zoneCode;

    /** 区域名称，如 北部核心保护区 */
    private String zoneName;

    /** 区域描述 */
    private String description;

    /** 区域面积（平方公里） */
    private BigDecimal areaKm2;

    /** 指挥台 SVG 地图中心横坐标 */
    private Double mapCenterX;

    /** 指挥台 SVG 地图中心纵坐标 */
    private Double mapCenterY;

    /** 区域火险等级：LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
