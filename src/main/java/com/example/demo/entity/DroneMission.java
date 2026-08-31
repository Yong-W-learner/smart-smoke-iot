package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 无人机巡航任务（火险事件现场复核）。
 *
 * 状态机：
 * PLANNED → DISPATCHED → EN_ROUTE → ON_SITE
 *        → RETURNED → COMPLETED
 *
 * 规则（透明，非AI）：
 * - confirmedFire=1（航拍确认火点）→ 所属事件 drone_confirmed=1，
 *   并重算事件优先级（强制 ≥95）。
 * - confirmedFire=0（未发现火点）→ 保留烟雾异常证据，等待人工复核。
 */
@Data
@TableName("drone_mission")
public class DroneMission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号，如 DM-20260901001 */
    private String missionCode;

    /** 无人机编号，如 DRONE-01 */
    private String droneId;

    /** 所属区域ID（forest_zone.id） */
    private Long zoneId;

    /** 所属区域名称 */
    private String zoneName;

    /** 目标监测节点编号，如 FS-N-001 */
    private String targetNodeCode;

    /** 任务类型：PATROL例行巡航 / FIRE_CONFIRM火点复核 */
    private String missionType;

    /** 任务状态：PLANNED / DISPATCHED / EN_ROUTE / ON_SITE / RETURNED / COMPLETED */
    private String state;

    /** 关联火险事件ID（alarm.id，火点复核任务有效） */
    private Long alarmId;

    /** 目标纬度 */
    private BigDecimal targetLat;

    /** 目标经度 */
    private BigDecimal targetLng;

    /** 目标 SVG 横坐标 */
    private Double targetMapX;

    /** 目标 SVG 纵坐标 */
    private Double targetMapY;

    /** 是否确认火点：0未确认 / 1已确认 */
    private Integer confirmedFire;

    /** 现场结果说明 */
    private String resultNote;

    /** 派发时间 */
    private LocalDateTime dispatchTime;

    /** 到达现场时间 */
    private LocalDateTime arriveTime;

    /** 返航时间 */
    private LocalDateTime returnTime;

    /** 任务完成时间 */
    private LocalDateTime completeTime;

    /** 任务创建时间 */
    private LocalDateTime createdAt;
}
