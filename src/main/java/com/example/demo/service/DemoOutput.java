package com.example.demo.service;

/**
 * DEMO 设备模拟的一条边缘端上报数据。
 *
 * 模拟 BearPi 上报的：
 * Smoke_Value / Baseline / Smoke_Ratio / Smoke_State
 */
public record DemoOutput(
        double smokeValue,
        double edgeBaseline,
        double edgeRatio,
        String edgeState
) {
}
