package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.EventPriorityService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员设备管理。
 *
 * 当前项目只有一台真实硬件，因此这里不开放“随便新增物理设备”，
 * 重点提供真实设备资料维护、房间调整和居民绑定。
 */
@RestController
@RequestMapping("/api/admin/devices")
public class AdminDeviceController {

    private final DeviceMapper deviceMapper;
    private final UserMapper userMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final EventPriorityService priorityService;

    public AdminDeviceController(DeviceMapper deviceMapper,
                                 UserMapper userMapper,
                                 SmokeRecordMapper smokeRecordMapper,
                                 EventPriorityService priorityService) {
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.priorityService = priorityService;
    }

    /**
     * 管理员设备列表。
     *
     * GET /api/admin/devices?healthStatus=NORMAL&online=1
     *
     * 附带每台设备的最新云端状态：
     * cloudState / riskScore / riskLevel。
     */
    @GetMapping
    public List<Map<String, Object>> listDevices(
            @RequestParam(required = false) String healthStatus,
            @RequestParam(required = false) Integer online,
            @RequestParam(required = false) String sourceType) {

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(healthStatus)
                && !"all".equalsIgnoreCase(healthStatus)) {
            wrapper.eq(Device::getHealthStatus, healthStatus.trim());
        }

        if (StringUtils.hasText(sourceType)
                && !"all".equalsIgnoreCase(sourceType)) {
            wrapper.eq(Device::getSourceType, sourceType.trim());
        }

        if (online != null && (online == 0 || online == 1)) {
            wrapper.eq(Device::getStatus, online);
        }

        wrapper.orderByAsc(Device::getDeviceId);

        List<Device> devices =
                deviceMapper.selectList(wrapper);

        Map<Long, SmokeRecord> latestMap =
                new HashMap<>();

        for (SmokeRecord record
                : smokeRecordMapper.selectLatestPerDevice()) {

            latestMap.put(record.getDeviceId(), record);
        }

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Device device : devices) {

            SmokeRecord latest =
                    latestMap.get(device.getDeviceId());

            Map<String, Object> item =
                    new LinkedHashMap<>();
            item.put("deviceId", device.getDeviceId());
            item.put("building", device.getBuilding());
            item.put("floor", device.getFloor());
            item.put("room", device.getRoom());
            item.put("status", device.getStatus());
            item.put("userId", device.getUserId());
            item.put("healthStatus", device.getHealthStatus());
            item.put("lastReportTime", device.getLastReportTime());
            item.put("consecutiveFailures", device.getConsecutiveFailures());
            item.put("iotDeviceId", device.getIotDeviceId());
            item.put("sourceType", device.getSourceType());
            item.put("cloudState",
                    latest == null ? null : latest.getCloudState());
            item.put("riskScore",
                    latest == null ? null : latest.getRiskScore());
            item.put("riskLevel",
                    priorityService.riskLevel(device, latest));

            result.add(item);
        }

        return result;
    }

    /**
     * 修改设备位置/绑定学生。
     *
     * PUT /api/admin/devices/{id}
     *
     * userId 有值：自动使用该学生的楼栋/楼层/房间。
     * userId 为 null：解除学生绑定，并使用请求中的位置。
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateDevice(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "设备不存在"
            );
        }

        Long userId = longValue(body.get("userId"));

        if (userId != null) {
            User resident = userMapper.selectById(userId);

            if (resident == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "绑定学生不存在"
                );
            }

            if (!"resident".equalsIgnoreCase(resident.getRole())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "设备只能绑定学生账号"
                );
            }

            if (resident.getBuilding() == null
                    || resident.getFloor() == null
                    || resident.getRoom() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "该学生尚未配置完整房间信息"
                );
            }

            device.setUserId(userId);
            device.setBuilding(resident.getBuilding());
            device.setFloor(resident.getFloor());
            device.setRoom(resident.getRoom());

        } else {
            Integer building = integerValue(body.get("building"));
            Integer floor = integerValue(body.get("floor"));
            Integer room = integerValue(body.get("room"));

            if (building == null || building <= 0
                    || floor == null || floor <= 0
                    || room == null || room <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "解除学生绑定后必须保留有效的设备位置"
                );
            }

            device.setUserId(null);
            device.setBuilding(building);
            device.setFloor(floor);
            device.setRoom(room);
        }

        /*
         * 位置唯一校验：目标寝室已被其它设备占用时拒绝修改，
         * 保证"一个寝室只对应一台设备"，
         * 彻底阻止 REAL 与 DEMO 占用同一寝室。
         */
        Long occupantCount =
                deviceMapper.selectCount(
                        new LambdaQueryWrapper<Device>()
                                .ne(Device::getDeviceId, id)
                                .eq(Device::getBuilding, device.getBuilding())
                                .eq(Device::getFloor, device.getFloor())
                                .eq(Device::getRoom, device.getRoom())
                );

        if (occupantCount != null && occupantCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该寝室位置已被其他设备占用，请选择其他位置"
            );
        }

        if (deviceMapper.updateById(device) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "设备资料修改失败"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "设备资料已更新");
        result.put("device", device);
        return result;
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户ID格式不正确"
            );
        }
    }

    private Integer integerValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "位置参数格式不正确"
            );
        }
    }
}
