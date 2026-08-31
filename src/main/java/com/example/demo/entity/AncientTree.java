package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 古树名木档案（生态资源）。
 *
 * 为每株古树建立"数字身份证"：
 * 编号 / 名称 / 树龄 / 保护等级 / 位置 / 健康状态，
 * 用于森林事件触发后的生态影响评估与生态回访。
 */
@Data
@TableName("ancient_tree")
public class AncientTree {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 古树编号，如 AT-001 */
    private String treeCode;

    /** 古树名称，如 北部古松 */
    private String treeName;

    /** 学名 */
    private String scientificName;

    /** 科属，如 松科·松属 */
    private String species;

    /** 树龄（年） */
    private Integer ageYears;

    /** 保护等级：一级 / 二级 / 三级 */
    private String protectionLevel;

    /** 所属区域ID（forest_zone.id） */
    private Long zoneId;

    /** 所属区域名称 */
    private String zoneName;

    /** 最近监测节点编号，如 FS-N-001 */
    private String nodeCode;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 指挥台 SVG 地图横坐标 */
    private Double mapX;

    /** 指挥台 SVG 地图纵坐标 */
    private Double mapY;

    /** 胸径（厘米） */
    private BigDecimal diameterCm;

    /** 树高（米） */
    private BigDecimal heightM;

    /** 健康状态：健康 / 亚健康 / 衰弱 / 濒危 */
    private String healthState;

    /** 档案描述 */
    private String description;

    /** 建档时间 */
    private LocalDateTime createdAt;
}
