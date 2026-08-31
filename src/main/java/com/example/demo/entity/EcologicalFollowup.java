package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生态回访任务。
 *
 * 森林火险事件处置到 RESOLVED（并已填写 recover_time）
 * 时，若事件点附近存在古树 / 野生动物栖息地，
 * 系统自动创建生态回访任务（asset_type + asset_id）。
 *
 * 状态机：PENDING → IN_PROGRESS → COMPLETED
 *
 * 闭环约束：存在未完成回访任务的森林事件，
 * 不允许关闭（CLOSED）。
 */
@Data
@TableName("ecological_followup")
public class EcologicalFollowup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联火险事件ID（alarm.id） */
    private Long alarmId;

    /** 生态资源类型：ANCIENT_TREE / WILDLIFE_HABITAT */
    private String assetType;

    /** 生态资源ID（ancient_tree.id / wildlife_habitat.id） */
    private Long assetId;

    /** 生态资源编号（AT-001 / WH-001） */
    private String assetCode;

    /** 生态资源名称 */
    private String assetName;

    /** 所属区域ID */
    private Long zoneId;

    /** 所属区域名称 */
    private String zoneName;

    /** 回访状态：PENDING / IN_PROGRESS / COMPLETED */
    private String state;

    /** 回访说明 */
    private String followupNote;

    /** 回访处理人 */
    private String handler;

    /** 期望完成时间 */
    private LocalDateTime dueTime;

    /** 实际完成时间 */
    private LocalDateTime completedTime;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
