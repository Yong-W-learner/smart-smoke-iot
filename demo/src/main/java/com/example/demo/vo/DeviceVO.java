package com.example.demo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备列表展示对象：设备信息 + 最新烟雾 + 归属居民
 */
@Data
public class DeviceVO {
    private Long deviceId;
    private Integer building;
    private Integer floor;
    private Integer room;
    private Integer status;      // 0离线 1在线
    private boolean online;      // 前端展示用
    private boolean simulated;   // false=华为云真机 / true=本地仿真设备
    private Double smokeConcentration; // 最新烟雾浓度
    private Integer alarm;       // 最新告警状态 0正常 1告警
    private String userName;     // 归属居民用户名
    private String phone;        // 归属居民手机号
    private LocalDateTime collectTime; // 最新采集时间（居民端“我的设备”用）
}
