package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm")
public class Alarm {

    /**
     * 告警记录主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对应设备 ID
     */
    private Long deviceId;

    /**
     * 告警产生时间
     */
    private LocalDateTime alarmTime;

    /**
     * 告警位置
     *
     * 例如：
     * 1栋1层101室
     */
    private String location;

    /**
     * ==========================================
     * 人工确认状态
     * ==========================================
     *
     * 0 = 尚未确认
     * 1 = 已人工确认
     *
     * 注意：
     * 这不是“烟雾是否仍在报警”。
     *
     * 烟雾是否已经恢复，
     * 由 recoverTime 判断。
     */
    private Integer acknowledged;

    /**
     * 人工确认告警的时间
     *
     * 用户点击“确认告警”时填写。
     */
    private LocalDateTime ackTime;

    /**
     * 告警类型
     *
     * 当前：
     * SMOKE
     *
     * 后续还可以扩展：
     * DEVICE_OFFLINE
     * SENSOR_FAULT
     */
    private String alarmType;

    /**
     * 告警等级
     *
     * 当前：
     * ALARM
     */
    private String alarmLevel;

    /**
     * 告警原因
     */
    private String reason;

    /**
     * 烟雾恢复正常时间
     *
     * null：
     * 告警环境尚未恢复
     *
     * 非 null：
     * 系统已经确认环境恢复
     */
    private LocalDateTime recoverTime;


    /* ==================================================
       人工处置流程（NEW → ACKNOWLEDGED → ON_SITE
                        → RESOLVED → CLOSED）
       ==================================================
       处置状态与"环境是否恢复"（recoverTime）相互独立：
       - 人工处置状态只记录管理员的操作进度；
       - 环境恢复仍由系统根据烟雾浓度自动写入 recoverTime，
         不能用人 工确认代替。
     */

    /**
     * 人工处置状态：
     * NEW / ACKNOWLEDGED / ON_SITE / RESOLVED / CLOSED
     *
     * null（历史数据）按 NEW 处理。
     */
    private String disposalState;

    /**
     * 处理人（确认/到场/关闭操作的管理员用户名）
     */
    private String handledBy;

    /**
     * 人工确认时间（确认告警时记录，与 ackTime 保持一致）
     */
    private LocalDateTime confirmedAt;

    /**
     * 标记到场时间（开始现场处理）
     */
    private LocalDateTime onSiteAt;

    /**
     * 关闭时间（事件关闭）
     */
    private LocalDateTime closeAt;

    /**
     * 处理备注（自由文本，如"现场无明显烟雾""已完成现场排查"等）。
     * 只记录人工描述，不自动推断烟雾来源。
     */
    private String disposalRemark;


    /* ==================================================
       森林火险事件扩展字段（sceneType = FOREST 时有效）
       ==================================================
       DORM_LEGACY：历史宿舍场景告警（building/floor/room 语义）；
       FOREST：森林火险事件（zone_id / 节点 / 评分语义）。
     */

    /** 场景类型：DORM_LEGACY / FOREST */
    private String sceneType;

    /** 所属森林区域ID（关联 forest_zone.id），FOREST 事件有效 */
    private Long zoneId;

    /** 火险可信度评分 0~100（烟雾异常证据分×0.7 + 云端+边缘证据 + 无人机确认） */
    private Integer fireConfidenceScore;

    /** 火险气象评分 0~100（温湿度/土壤湿度综合） */
    private Integer fireWeatherScore;

    /** 古树影响评分 0~100（事件点距古树越近越高） */
    private Integer ancientTreeImpactScore;

    /** 野生动物栖息地影响评分 0~100 */
    private Integer wildlifeImpactScore;

    /** 事件优先级总分 0~100 */
    private Integer priorityScore;

    /** 事件优先级等级：RED / ORANGE / YELLOW / LOW */
    private String priorityLevel;

    /** 事件优先级判定依据说明（透明规则，不含AI描述） */
    private String priorityReason;

    /** 无人机是否已确认火点：0未确认 / 1已确认 */
    private Integer droneConfirmed;
}