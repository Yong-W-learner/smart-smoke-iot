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
}