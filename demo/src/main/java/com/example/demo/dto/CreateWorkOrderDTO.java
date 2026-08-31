package com.example.demo.dto;

import lombok.Data;

@Data
public class CreateWorkOrderDTO {
    private Long reporterId;    //报修居民id
    private String title;       //报修标题
    private String description; //报修描述
}
