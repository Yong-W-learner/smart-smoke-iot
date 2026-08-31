package com.example.demo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReviewVO {
    private Long id;
    private Long deviceId;
    private Integer alarmLevel;
    private Double smokeConcentration;
    private String imageBase64;
    private Integer building;
    private Integer floor;
    private Integer room;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    // AI 复核结果
    private String aiVerdict;
    private String aiBasis;
    private Object aiDetections;  // 解析后的 JSON 数组
    private Object aiBoxes;
}
