package com.example.demo.controller;

import com.example.demo.service.AlarmService;
import com.example.demo.service.SmokeDecisionService;
import com.example.demo.vo.SmokeDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class SmokeSimulationController {

    @Autowired
    private SmokeDecisionService smokeDecisionService;

    @Autowired
    private AlarmService alarmService;

    /**
     * 模拟一条烟雾数据
     *
     * 例如：
     * /api/test/smoke?value=20
     * /api/test/smoke?value=60
     */
    @GetMapping("/smoke")
    public Map<String, Object> simulateSmoke(
            @RequestParam double value) {

        SmokeDecision decision =
                smokeDecisionService.analyze(value);

        alarmService.handleSmokeDecision(
                1L,
                decision.getState(),
                "【模拟测试】" + decision.getReason()
        );

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("smokeValue", value);
        result.put("cloudState", decision.getState());
        result.put("riskScore", decision.getRiskScore());
        result.put("reason", decision.getReason());

        return result;
    }
}