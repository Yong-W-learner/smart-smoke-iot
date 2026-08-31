package com.example.demo.service;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 单台设备的云端智能判定状态。
 *
 * 每个设备持有独立的一套状态：
 * 滑动窗口、云端背景基线、当前云端状态、恢复计数。
 *
 * 之前 SmokeDecisionService 用全局字段保存，
 * 多台设备会互相污染基线 / 滑窗 / 报警状态。
 * 现在按 deviceId 各存一份。
 */
public class DeviceDecisionContext {

    /**
     * 最近烟雾数据滑动窗口
     */
    private final Deque<Double> smokeWindow =
            new ArrayDeque<>();

    /**
     * 云端稳定背景基线
     */
    private Double cloudBaseline = null;

    /**
     * 当前云端状态：NORMAL / WARNING / ALARM
     */
    private String currentState = "NORMAL";

    /**
     * ALARM 后已经连续恢复正常多少次
     */
    private int recoveryCount = 0;

    public Deque<Double> getSmokeWindow() {
        return smokeWindow;
    }

    public Double getCloudBaseline() {
        return cloudBaseline;
    }

    public void setCloudBaseline(Double cloudBaseline) {
        this.cloudBaseline = cloudBaseline;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public int getRecoveryCount() {
        return recoveryCount;
    }

    public void setRecoveryCount(int recoveryCount) {
        this.recoveryCount = recoveryCount;
    }
}
