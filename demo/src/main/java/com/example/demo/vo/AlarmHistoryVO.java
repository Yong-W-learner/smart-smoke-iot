package com.example.demo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AlarmHistoryVO {
    private Long deviceId;
    private Integer building;
    private Integer floor;
    private Integer room;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;
    private Double peakConcentration; // 本次事件峰值浓度
    private Integer maxLevel;         // 最高警情等级 1/2/3
    private Integer recordCount;      // 本次事件包含的采集点数
}
