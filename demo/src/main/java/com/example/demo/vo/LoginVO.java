package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    private String token;
    private String username;
    private String role;
    private Integer building;
    private Integer floor;
    private Integer room;
    private String phone;
    private String jobNum;
    private Long id;        // 用户主键
    private Long deviceId;  // 居民绑定的传感器编号（管理员为 null）
}
