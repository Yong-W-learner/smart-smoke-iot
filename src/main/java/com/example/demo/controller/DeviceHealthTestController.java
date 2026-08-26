package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.service.DeviceHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/health")
public class DeviceHealthTestController {

    @Autowired
    private DeviceHealthService deviceHealthService;

    @Autowired
    private DeviceMapper deviceMapper;


    /**
     * 模拟传感器健康检查。
     *
     * 示例：
     *
     * /api/test/health/sensor
     * ?smoke=20
     * &baseline=20
     * &ratio=8
     * &state=NORMAL
     */
    @GetMapping("/sensor")
    public Device testSensorHealth(
            @RequestParam Double smoke,
            @RequestParam Double baseline,
            @RequestParam Double ratio,
            @RequestParam String state) {

        /*
         * 模拟：
         * 设备仍然在线，并且有新的数据上报。
         */
        deviceHealthService.handleNewReport(
                1L
        );

        /*
         * 对模拟数据执行传感器健康检查。
         */
        deviceHealthService.checkSensorHealth(
                1L,
                smoke,
                baseline,
                ratio,
                state
        );

        /*
         * 返回数据库中最新设备状态。
         */
        return deviceMapper.selectById(
                1L
        );
    }
}