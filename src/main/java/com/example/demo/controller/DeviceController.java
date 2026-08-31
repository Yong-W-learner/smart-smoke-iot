package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.service.DataScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DataScopeService dataScopeService;

    public DeviceController(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    /**
     * resident：只返回本人/本房间设备。
     * admin：返回全部设备。
     */
    @GetMapping("/device/list")
    public List<Device> getDeviceList() {
        return dataScopeService.getVisibleDevices();
    }
}
