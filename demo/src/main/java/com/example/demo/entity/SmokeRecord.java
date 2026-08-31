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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime collectTime;
    private Double smokeConcentration;
    private Integer alarm; //0正常 1告警
}
