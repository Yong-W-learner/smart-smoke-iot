package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("forest_camera_review")
public class ForestCameraReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sensorId;
    private String zone;
    private String level;
    private Double smokeConcentration;
    private String imageBase64;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
    private String aiVerdict;
    private String aiBasis;
    private String aiDetections;
    private String aiBoxes;
}
