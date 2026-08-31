package com.example.demo.service;

import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.vo.SmokeDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 烟雾上报公共链路。
 *
 * 真实设备（SmokeCollectTask）与
 * DEMO 模拟设备（DemoDeviceTask）都走同一条链路：
 *
 * SmokeDecisionService.analyze
 *  → 写 smoke_record
 *  → AlarmService 告警闭环
 *  → DeviceHealthService 健康状态
 *
 * 保证模拟数据和真实数据行为完全一致。
 */
@Service
public class SmokeReportService {

    private static final Logger log =
            LoggerFactory.getLogger(SmokeReportService.class);

    private final SmokeDecisionService smokeDecisionService;

    private final SmokeRecordMapper smokeRecordMapper;

    private final AlarmService alarmService;

    private final DeviceHealthService deviceHealthService;

    public SmokeReportService(
            SmokeDecisionService smokeDecisionService,
            SmokeRecordMapper smokeRecordMapper,
            AlarmService alarmService,
            DeviceHealthService deviceHealthService) {

        this.smokeDecisionService = smokeDecisionService;
        this.smokeRecordMapper = smokeRecordMapper;
        this.alarmService = alarmService;
        this.deviceHealthService = deviceHealthService;
    }

    /**
     * 处理一条新的设备烟雾采样，返回云端判定结果。
     *
     * @param deviceId     数据库设备ID
     * @param smokeValue   当前烟雾浓度
     * @param edgeBaseline 边缘端动态基线（可空）
     * @param edgeRatio    边缘端 smoke/baseline（可空）
     * @param edgeState    边缘端状态 NORMAL/PREWARNING/ALARM（可空）
     */
    public SmokeDecision persistSample(
            Long deviceId,
            double smokeValue,
            Double edgeBaseline,
            Double edgeRatio,
            String edgeState) {

        /*
         * 1. 云端智能判定
         */
        SmokeDecision decision =
                smokeDecisionService.analyze(
                        deviceId,
                        smokeValue
                );

        /*
         * 2. 写入 smoke_record
         */
        SmokeRecord record =
                new SmokeRecord();

        record.setDeviceId(deviceId);
        record.setCollectTime(LocalDateTime.now());
        record.setSmokeConcentration(smokeValue);

        record.setEdgeBaseline(edgeBaseline);
        record.setSmokeRatio(edgeRatio);
        record.setEdgeState(edgeState);

        record.setCloudState(decision.getState());
        record.setRiskScore(decision.getRiskScore());
        record.setDecisionReason(decision.getReason());

        record.setAlarm(
                "ALARM".equals(decision.getState())
                        ? 1
                        : 0
        );

        smokeRecordMapper.insert(record);

        /*
         * 3. 正式告警事件闭环
         *
         * 携带 riskScore（烟雾异常证据分）与 edgeState，
         * 供森林火险事件计算火险可信度。
         */
        alarmService.handleSmokeDecision(
                deviceId,
                decision.getState(),
                decision.getReason(),
                decision.getRiskScore(),
                edgeState
        );

        /*
         * 4. 新真实数据 -> 基础健康状态 NORMAL
         */
        deviceHealthService.handleNewReport(
                deviceId
        );

        /*
         * 5. 传感器数据健康检查
         */
        deviceHealthService.checkSensorHealth(
                deviceId,
                smokeValue,
                edgeBaseline,
                edgeRatio,
                edgeState
        );

        log.info(
                "设备={} | 烟雾={} | 边缘基线={} | 边缘倍率={} | 边缘状态={} | 云端状态={} | 风险评分={} | 原因={}",
                deviceId,
                smokeValue,
                edgeBaseline,
                edgeRatio,
                edgeState,
                decision.getState(),
                decision.getRiskScore(),
                decision.getReason()
        );

        return decision;
    }
}
