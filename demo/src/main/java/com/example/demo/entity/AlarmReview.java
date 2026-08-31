package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alarm_review")
public class AlarmReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Integer alarmLevel; //0正常 1一级 2二级 3三级
    private Double smokeConcentration;
    private String imageBase64; //现场照片(base64)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    // AI 复核结果（YOLO 视觉识别 + 传感器融合）
    private String aiVerdict;     // normal正常 / steam水汽 / smoke烟雾 / fire明火
    private String aiBasis;       // 判定依据文字
    private String aiDetections;  // 检测列表 JSON：[{label, conf}]
    private String aiBoxes;       // 检测框 JSON：[{x,y,w,h,label,conf}] 百分比坐标
}
