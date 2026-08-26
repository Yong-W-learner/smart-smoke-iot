package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Device;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    public AdminDeviceController(DeviceMapper deviceMapper,
                                 UserMapper userMapper) {
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
    }

    /**
     * 管理员设备列表。
     *
     * GET /api/admin/devices?healthStatus=NORMAL&online=1
     */
    @GetMapping
    public List<Device> listDevices(
            @RequestParam(required = false) String healthStatus,
            @RequestParam(required = false) Integer online) {

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(healthStatus)
                && !"all".equalsIgnoreCase(healthStatus)) {
            wrapper.eq(Device::getHealthStatus, healthStatus.trim());
        }

        if (online != null && (online == 0 || online == 1)) {
            wrapper.eq(Device::getStatus, online);
        }

        wrapper.orderByAsc(Device::getDeviceId);
        return deviceMapper.selectList(wrapper);
    }

    /**
     * 修改设备位置/绑定居民。
     *
     * PUT /api/admin/devices/{id}
     *
     * userId 有值：自动使用该居民的楼栋/楼层/房间。
     * userId 为 null：解除居民绑定，并使用请求中的位置。
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
                        "绑定居民不存在"
                );
            }

            if (!"resident".equalsIgnoreCase(resident.getRole())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "设备只能绑定居民账号"
                );
            }

            if (resident.getBuilding() == null
                    || resident.getFloor() == null
                    || resident.getRoom() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "该居民尚未配置完整房间信息"
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
                        "解除居民绑定后必须保留有效的设备位置"
                );
            }

            device.setUserId(null);
            device.setBuilding(building);
            device.setFloor(floor);
            device.setRoom(room);
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
