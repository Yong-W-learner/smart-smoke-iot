package com.example.demo.dto;

import lombok.Data;

@Data
public class AcceptWorkOrderDTO {
    private Long id;        //工单id
    private Long repairerId; //接单维修员id
}
