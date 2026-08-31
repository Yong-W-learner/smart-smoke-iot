package com.example.demo.dto;

import lombok.Data;

@Data
public class CloseWorkOrderDTO {
    private Long id;         //工单id
    private Long operatorId; //操作者id（维修员或管理员）
    private String remark;   //关闭备注
}
