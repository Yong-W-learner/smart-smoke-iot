package com.example.demo.dto;

import lombok.Data;

@Data
public class RegisterDTO {
    private String username;
    private Integer building;
    private Integer floor;
    private Integer room;
    private String phone;
    private String password;
}
