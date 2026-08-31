package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 古树巡护检查记录。
 *
 * 巡护员定期检查古树健康状态，
 * 记录检查时间 / 检查人 / 健康状态 / 病虫害风险 / 现场说明。
 */
@Data
@TableName("ancient_tree_inspection")
public class AncientTreeInspection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联古树ID（ancient_tree.id） */
    private Long treeId;

    /** 古树编号 */
    private String treeCode;

    /** 检查时间 */
    private LocalDateTime inspectionDate;

    /** 检查人 */
    private String inspector;

    /** 健康状态：HEALTHY / FAIR / POOR / CRITICAL */
    private String healthState;

    /** 病虫害风险：无 / 低 / 中 / 高 */
    private String pestRisk;

    /** 检查说明 */
    private String description;

    /** 记录时间 */
    private LocalDateTime createdAt;
}
