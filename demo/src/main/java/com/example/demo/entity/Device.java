package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long deviceId;
    private Integer building;
    private Integer floor;
    private Integer room;
    private Integer status; //0离线 1在线
    private Long userId; //外键，关联sys_user.id，设备归属居民
}
