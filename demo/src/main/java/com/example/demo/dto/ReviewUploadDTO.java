package com.example.demo.dto;

import lombok.Data;

@Data
public class ReviewUploadDTO {
    private Long deviceId;
    private Integer alarmLevel;
    private Double smokeConcentration;
    private String imageBase64;
}
