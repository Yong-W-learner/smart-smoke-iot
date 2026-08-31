package com.example.demo.vo;

import lombok.Data;

/**
 * 账号展示对象（不含密码，供账号维护员查看）
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String role;      // resident居民 / admin小区管理员 / maintain账号维护员
    private Integer building;
    private Integer floor;
    private Integer room;
    private String phone;
    private String jobNum;    // 管理员工号，居民为null
}
