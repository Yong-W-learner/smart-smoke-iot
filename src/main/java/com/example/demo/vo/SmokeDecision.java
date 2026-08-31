package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmokeDecision {

    /**
     * NORMAL / WARNING / ALARM
     */
    private String state;

    /**
     * 风险评分 0~100
     */
    private double riskScore;

    /**
     * 判定原因
     */
    private String reason;
}