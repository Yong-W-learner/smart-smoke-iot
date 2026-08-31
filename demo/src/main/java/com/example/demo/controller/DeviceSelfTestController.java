package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.CreateDeviceSelfTestDTO;
import com.example.demo.entity.Device;
import com.example.demo.entity.DeviceSelfTest;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.DeviceSelfTestMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 设备声光、通信和数据上报自检记录。 */
@RestController
@RequestMapping("/api/device-self-test")
public class DeviceSelfTestController {

    private final DeviceSelfTestMapper selfTestMapper;
    private final DeviceMapper deviceMapper;
    private final UserMapper userMapper;

    public DeviceSelfTestController(DeviceSelfTestMapper selfTestMapper,
                                    DeviceMapper deviceMapper,
                                    UserMapper userMapper) {
        this.selfTestMapper = selfTestMapper;
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
    }

    @GetMapping("/list")
    public List<DeviceSelfTest> list(@RequestParam(required = false) Long deviceId) {
        LambdaQueryWrapper<DeviceSelfTest> w = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            w.eq(DeviceSelfTest::getDeviceId, deviceId);
        }
        w.orderByDesc(DeviceSelfTest::getTestTime).last("LIMIT 50");
        return selfTestMapper.selectList(w);
    }

    @PostMapping("/create")
    public Result<DeviceSelfTest> create(@RequestBody CreateDeviceSelfTestDTO dto) {
        if (dto.getDeviceId() == null || dto.getOperatorId() == null) {
            return Result.fail("设备和操作人不能为空");
        }
        Device device = deviceMapper.selectById(dto.getDeviceId());
        if (device == null) {
            return Result.fail("设备不存在");
        }
        User operator = userMapper.selectById(dto.getOperatorId());
        if (operator == null || !"admin".equals(operator.getRole())) {
            return Result.fail("仅管理员可以保存设备自检结果");
        }

        DeviceSelfTest record = new DeviceSelfTest();
        record.setDeviceId(dto.getDeviceId());
        record.setOperatorId(operator.getId());
        record.setOperatorName(operator.getUsername());
        record.setTestTime(LocalDateTime.now());
        record.setOnlineOk(Boolean.TRUE.equals(dto.getOnlineOk()));
        record.setTelemetryOk(Boolean.TRUE.equals(dto.getTelemetryOk()));
        record.setBeepCommandOk(Boolean.TRUE.equals(dto.getBeepCommandOk()));
        record.setBeepObservedOk(Boolean.TRUE.equals(dto.getBeepObservedOk()));
        record.setLedCommandOk(Boolean.TRUE.equals(dto.getLedCommandOk()));
        record.setLedObservedOk(Boolean.TRUE.equals(dto.getLedObservedOk()));
        record.setRemark(dto.getRemark());

        boolean passed = record.getOnlineOk() && record.getTelemetryOk()
                && record.getBeepCommandOk() && record.getBeepObservedOk()
                && record.getLedCommandOk() && record.getLedObservedOk();
        record.setResult(passed ? "passed" : "failed");
        selfTestMapper.insert(record);
        return Result.ok(record);
    }
}
