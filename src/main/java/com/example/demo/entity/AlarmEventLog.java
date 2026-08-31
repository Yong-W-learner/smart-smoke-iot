package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全事件操作日志 / 时间线。
 *
 * 记录一个告警从产生到关闭的完整节点：
 * 系统发现异常并创建告警 → 管理员确认 → 标记到场 → 填写处置结果
 * → 烟雾恢复 → 事件关闭。
 *
 * 展示顺序：eventTime 升序。
 */
@Data
@TableName("alarm_event_log")
public class AlarmEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联告警 ID
     */
    private Long alarmId;

    /**
     * 关联设备 ID
     */
    private Long deviceId;

    /**
     * 事件类型（机器可读）：
     * ALARM_CREATED / CONFIRMED / ON_SITE / RESOLVED
     * / SMOKE_RECOVERED / CLOSED
     */
    private String eventType;

    /**
     * 事件中文标签（界面展示）
     */
    private String eventLabel;

    /**
     * 补充说明（如告警原因 / 处理备注）
     */
    private String description;

    /**
     * 操作人（系统事件为 null）
     */
    private String operator;

    /**
     * 事件发生时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "Asia/Shanghai")
    private LocalDateTime eventTime;
}
