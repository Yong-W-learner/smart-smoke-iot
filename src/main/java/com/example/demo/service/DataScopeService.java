package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Device;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一的数据权限服务。
 *
 * admin：可以访问全部设备。
 * resident：只能访问自己绑定设备，或自己房间中的设备。
 *
 * 森林巡护员内部复用 resident 角色：当 resident 用户绑定了巡护区域
 * （sys_user.zone_id）时，视为巡护员，只能访问本人巡护分区内的
 * 森林监测节点；未绑定分区视为普通居民（房间语义）。
 *
 * 之所以同时兼容 user_id 与 房间位置，是因为项目早期已有设备数据，
 * 设备可能尚未回填 user_id；这样可以在不破坏现有数据的前提下完成权限升级。
 */
@Service
public class DataScopeService {

    private final UserMapper userMapper;
    private final DeviceMapper deviceMapper;

    public DataScopeService(UserMapper userMapper,
                            DeviceMapper deviceMapper) {
        this.userMapper = userMapper;
        this.deviceMapper = deviceMapper;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }

        String username = authentication.getName();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .last("LIMIT 1")
        );

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在"
            );
        }

        return user;
    }

    public boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    /**
     * 是否为森林巡护员。
     *
     * 内部复用 resident 角色：居民用户绑定了巡护区域（zone_id）即视为巡护员。
     */
    public boolean isPatrol(User user) {
        return user != null
                && "resident".equalsIgnoreCase(user.getRole())
                && user.getZoneId() != null;
    }

    public List<Device> getVisibleDevices() {
        User user = getCurrentUser();

        if (isAdmin(user)) {
            return deviceMapper.selectList(
                    new LambdaQueryWrapper<Device>()
                            .orderByAsc(Device::getDeviceId)
            );
        }

        if (isPatrol(user)) {

            if (user.getZoneId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "巡护员尚未绑定巡护区域"
                );
            }

            return deviceMapper.selectList(
                    new LambdaQueryWrapper<Device>()
                            .eq(Device::getZoneId, user.getZoneId())
                            .orderByAsc(Device::getDeviceId)
            );
        }

        if (!"resident".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账户角色无权访问设备数据"
            );
        }

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        wrapper.and(scope -> {
            boolean hasLocation = user.getBuilding() != null
                    && user.getFloor() != null
                    && user.getRoom() != null;

            scope.eq(Device::getUserId, user.getId());

            if (hasLocation) {
                scope.or(location -> location
                        .eq(Device::getBuilding, user.getBuilding())
                        .eq(Device::getFloor, user.getFloor())
                        .eq(Device::getRoom, user.getRoom())
                );
            }
        });

        wrapper.orderByAsc(Device::getDeviceId);

        return deviceMapper.selectList(wrapper);
    }

    public List<Long> getVisibleDeviceIds() {
        List<Device> devices = getVisibleDevices();

        if (devices == null || devices.isEmpty()) {
            return Collections.emptyList();
        }

        return devices.stream()
                .map(Device::getDeviceId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
    }

    public boolean canAccessDevice(Long deviceId) {
        if (deviceId == null) {
            return false;
        }

        User user = getCurrentUser();

        if (isAdmin(user)) {
            return deviceMapper.selectById(deviceId) != null;
        }

        Device device = deviceMapper.selectById(deviceId);

        if (device == null) {
            return false;
        }

        if (isPatrol(user)) {
            return user.getZoneId() != null
                    && user.getZoneId().equals(device.getZoneId());
        }

        if (device.getUserId() != null
                && device.getUserId().equals(user.getId())) {
            return true;
        }

        return user.getBuilding() != null
                && user.getFloor() != null
                && user.getRoom() != null
                && user.getBuilding().equals(device.getBuilding())
                && user.getFloor().equals(device.getFloor())
                && user.getRoom().equals(device.getRoom());
    }

    public void assertCanAccessDevice(Long deviceId) {
        if (!canAccessDevice(deviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "无权访问该设备的数据"
            );
        }
    }
}
