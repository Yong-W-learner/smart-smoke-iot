package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 野生动物栖息地（生态资源）。
 *
 * 只维护栖息地空间范围与保护等级，
 * 不做动物个体识别（不涉及AI）。
 */
@Data
@TableName("wildlife_habitat")
public class WildlifeHabitat {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 栖息地编号，如 WH-001 */
    private String habitatCode;

    /** 栖息地名称 */
    private String habitatName;

    /** 所属区域ID（forest_zone.id） */
    private Long zoneId;

    /** 所属区域名称 */
    private String zoneName;

    /** 保护等级：CORE核心 / HIGH重点 / MEDIUM一般 */
    private String protectionLevel;

    /** 主要物种描述 */
    private String speciesKeywords;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 指挥台 SVG 地图横坐标 */
    private Double mapX;

    /** 指挥台 SVG 地图纵坐标 */
    private Double mapY;

    /** 面积（平方公里） */
    private BigDecimal areaKm2;

    /** 火险敏感等级：HIGH / MEDIUM / LOW */
    private String riskLevel;

    /** 栖息地描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
