package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_self_test")
public class DeviceSelfTest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Long operatorId;
    private String operatorName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime testTime;
    private Boolean onlineOk;
    private Boolean telemetryOk;
    private Boolean beepCommandOk;
    private Boolean beepObservedOk;
    private Boolean ledCommandOk;
    private Boolean ledObservedOk;
    private String result;
    private String remark;
}
