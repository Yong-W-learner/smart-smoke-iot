package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("smoke_record")
public class SmokeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "Asia/Shanghai")
    private LocalDateTime collectTime;

    // 当前烟雾浓度
    private Double smokeConcentration;

    // 兼容当前页面：0正常 1报警
    private Integer alarm;

    // ===== 边缘端判定信息 =====

    // NORMAL / PREWARNING / ALARM
    private String edgeState;

    // BearPi 当前动态基线
    private Double edgeBaseline;

    // Smoke_Value / baseline
    private Double smokeRatio;

    // ===== 云端判定信息 =====

    // NORMAL / WARNING / ALARM
    private String cloudState;

    // 0~100
    private Double riskScore;

    // 为什么这样判断
    private String decisionReason;
}