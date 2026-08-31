package com.example.demo.dto;

import lombok.Data;

@Data
public class CreateDeviceSelfTestDTO {
    private Long deviceId;
    private Long operatorId;
    private Boolean onlineOk;
    private Boolean telemetryOk;
    private Boolean beepCommandOk;
    private Boolean beepObservedOk;
    private Boolean ledCommandOk;
    private Boolean ledObservedOk;
    private String remark;
}
