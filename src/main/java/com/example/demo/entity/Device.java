package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long deviceId;

    private Integer building;

    private Integer floor;

    private Integer room;

    // 0离线 1在线
    private Integer status;

    // 外键，关联 sys_user.id
    private Long userId;

    // NORMAL / OFFLINE / STALE / SENSOR_FAULT
    private String healthStatus;

    // 最后收到有效设备数据的时间
    private LocalDateTime lastReportTime;

    // 连续获取华为云数据失败次数
    private Integer consecutiveFailures;
}