package com.example.demo.dto;

import lombok.Data;

@Data
public class ForestReviewUploadDTO {
    private String sensorId;
    private String zone;
    private String level;
    private Double smokeConcentration;
    private String imageBase64;
}
