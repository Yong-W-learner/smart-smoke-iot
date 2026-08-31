package com.example.demo.dto;

import lombok.Data;

/**
 * 新增设备入参
 */
@Data
public class DeviceDTO {
    private Long deviceId;    // 设备编号
    private Integer building; // 栋
    private Integer floor;    // 层
    private Integer room;     // 户
    private String phone;     // 居民手机号（用于关联居民）
}
