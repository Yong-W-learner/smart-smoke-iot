package com.example.demo.vo;

import lombok.Data;

@Data
public class UserInfoVO {
    private String username;
    private String role;
    private Integer building;
    private Integer floor;
    private Integer room;
    private String phone;
    private String jobNum;
}
