package com.example.demo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForestReviewVO {
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
    private Object aiDetections;
    private Object aiBoxes;
}
