package com.example.demo.controller;

import com.example.demo.service.ForestEquipmentSelfTestService;
import com.example.demo.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/forest/equipment")
public class ForestEquipmentSelfTestController {

    private final ForestEquipmentSelfTestService service;

    public ForestEquipmentSelfTestController(ForestEquipmentSelfTestService service) {
        this.service = service;
    }

    @PostMapping("/{id}/self-test")
    public Result<Map<String, Object>> start(@PathVariable String id) {
        Map<String, Object> data = service.start(id);
        return data == null ? Result.fail("设备不存在") : Result.ok(data);
    }

    @GetMapping("/self-tests/{testNo}")
    public Result<Map<String, Object>> detail(@PathVariable String testNo) {
        Map<String, Object> data = service.get(testNo);
        return data == null ? Result.fail("自检任务不存在") : Result.ok(data);
    }
}
