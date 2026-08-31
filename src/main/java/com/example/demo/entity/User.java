package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String role; //resident居民 admin管理员
    private Integer building;
    private Integer floor;
    private Integer room;

    /** 巡护区域ID（关联 forest_zone.id，巡护员/管理员绑定片区） */
    private Long zoneId;

    private String phone;
    private String jobNum; //管理员工号，居民null
    private String password;
}
