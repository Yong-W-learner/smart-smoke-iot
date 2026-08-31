package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.Device;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员用户管理接口。
 *
 * 功能：
 * 1. 查看/搜索用户
 * 2. 新增居民或管理员
 * 3. 编辑用户资料与角色
 * 4. 管理员重置用户密码
 * 5. 删除用户（自动解除设备绑定）
 *
 * 注意：所有返回结果都不会包含 password 字段。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;

    public UserController(UserMapper userMapper,
                          DeviceMapper deviceMapper,
                          PasswordEncoder passwordEncoder,
                          DataScopeService dataScopeService) {
        this.userMapper = userMapper;
        this.deviceMapper = deviceMapper;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
    }

    /**
     * 用户列表。
     *
     * GET /api/admin/users?keyword=xxx&role=resident
     */
    @GetMapping
    public List<Map<String, Object>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            wrapper.and(w -> w
                    .like(User::getUsername, k)
                    .or()
                    .like(User::getPhone, k)
                    .or()
                    .like(User::getJobNum, k));
        }

        if (StringUtils.hasText(role)
                && !"all".equalsIgnoreCase(role)) {
            wrapper.eq(User::getRole, role.trim().toLowerCase());
        }

        wrapper.orderByAsc(User::getId);

        List<User> users = userMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            result.add(toSafeMap(user));
        }

        return result;
    }

    /**
     * 新增用户。
     *
     * POST /api/admin/users
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(
            @RequestBody Map<String, Object> body) {

        String username = stringValue(body.get("username"));
        String password = stringValue(body.get("password"));
        String role = stringValue(body.get("role"));
        String phone = stringValue(body.get("phone"));
        String jobNum = stringValue(body.get("jobNum"));

        if (!StringUtils.hasText(role)) {
            role = "resident";
        }
        role = role.toLowerCase();

        validateUsername(username, null);
        validatePassword(password);
        validateRole(role);

        User user = new User();
        user.setUsername(username.trim());
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(password));

        if ("resident".equals(role)) {

            Long zoneId = longValue(body.get("zoneId"));

            if (zoneId != null && zoneId > 0) {
                // 巡护员：内部复用 resident 角色，绑定巡护分区（zone_id）
                user.setBuilding(null);
                user.setFloor(null);
                user.setRoom(null);
                user.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
                user.setJobNum(StringUtils.hasText(jobNum) ? jobNum.trim() : null);
                user.setZoneId(zoneId);

            } else {
                // 普通居民：房间绑定
                Integer building = integerValue(body.get("building"));
                Integer floor = integerValue(body.get("floor"));
                Integer room = integerValue(body.get("room"));

                validateLocation(building, floor, room);

                if (!StringUtils.hasText(phone)) {
                    throw badRequest("学生手机号不能为空");
                }

                user.setBuilding(building);
                user.setFloor(floor);
                user.setRoom(room);
                user.setPhone(phone.trim());
                user.setJobNum(null);
                user.setZoneId(null);
            }

        } else {
            if (!StringUtils.hasText(jobNum)) {
                throw badRequest("管理员工号不能为空");
            }

            user.setBuilding(null);
            user.setFloor(null);
            user.setRoom(null);
            user.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
            user.setJobNum(jobNum.trim());
            user.setZoneId(null);
        }

        if (userMapper.insert(user) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "新增用户失败"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "用户创建成功");
        result.put("user", toSafeMap(user));
        return result;
    }

    /**
     * 编辑用户。
     *
     * PUT /api/admin/users/{id}
     */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        User target = requireUser(id);
        User currentAdmin = dataScopeService.getCurrentUser();

        String username = stringValue(body.get("username"));
        String role = stringValue(body.get("role"));
        String phone = stringValue(body.get("phone"));
        String jobNum = stringValue(body.get("jobNum"));

        if (!StringUtils.hasText(username)) {
            username = target.getUsername();
        }

        if (!StringUtils.hasText(role)) {
            role = target.getRole();
        }
        role = role.toLowerCase();

        validateUsername(username, id);
        validateRole(role);

        // JWT 的主体是用户名。当前管理员若直接改自己的用户名，
        // 现有 JWT 会立即找不到用户，因此要求退出后通过单独流程处理。
        if (currentAdmin.getId().equals(id)
                && !currentAdmin.getUsername().equals(username.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "不能在当前会话中修改自己的用户名"
            );
        }

        // 防止管理员把自己降成居民，导致当前管理会话失去意义。
        if (currentAdmin.getId().equals(id)
                && !"admin".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "不能修改当前登录管理员自己的角色"
            );
        }

        target.setUsername(username.trim());
        target.setRole(role);

        if ("resident".equals(role)) {

            Long zoneId = longValue(body.get("zoneId"));

            if (zoneId != null && zoneId > 0) {
                // 巡护员：内部复用 resident 角色，绑定巡护分区（zone_id）
                target.setBuilding(null);
                target.setFloor(null);
                target.setRoom(null);
                target.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
                target.setJobNum(StringUtils.hasText(jobNum) ? jobNum.trim() : null);
                target.setZoneId(zoneId);

                // 巡护员不再作为某个房间设备的绑定居民。
                deviceMapper.update(
                        null,
                        new LambdaUpdateWrapper<Device>()
                                .eq(Device::getUserId, id)
                                .set(Device::getUserId, null)
                );

            } else {
                // 普通居民：房间绑定
                Integer building = integerValue(body.get("building"));
                Integer floor = integerValue(body.get("floor"));
                Integer room = integerValue(body.get("room"));

                validateLocation(building, floor, room);

                if (!StringUtils.hasText(phone)) {
                    throw badRequest("学生手机号不能为空");
                }

                target.setBuilding(building);
                target.setFloor(floor);
                target.setRoom(room);
                target.setPhone(phone.trim());
                target.setJobNum(null);
                target.setZoneId(null);

                // 已经明确绑定给该居民的设备，位置跟随居民房间变化。
                deviceMapper.update(
                        null,
                        new LambdaUpdateWrapper<Device>()
                                .eq(Device::getUserId, id)
                                .set(Device::getBuilding, building)
                                .set(Device::getFloor, floor)
                                .set(Device::getRoom, room)
                );
            }

        } else {
            if (!StringUtils.hasText(jobNum)) {
                throw badRequest("管理员工号不能为空");
            }

            target.setBuilding(null);
            target.setFloor(null);
            target.setRoom(null);
            target.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
            target.setJobNum(jobNum.trim());
            target.setZoneId(null);

            // 用户升级为管理员后，不再作为某个房间设备的绑定居民。
            deviceMapper.update(
                    null,
                    new LambdaUpdateWrapper<Device>()
                            .eq(Device::getUserId, id)
                            .set(Device::getUserId, null)
            );
        }

        if (userMapper.updateById(target) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "用户资料修改失败"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "用户资料已更新");
        result.put("user", toSafeMap(target));
        return result;
    }

    /**
     * 管理员重置用户密码。
     *
     * PUT /api/admin/users/{id}/password
     */
    @PutMapping("/{id}/password")
    public Map<String, Object> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        User target = requireUser(id);
        String newPassword = stringValue(body.get("newPassword"));

        validatePassword(newPassword);

        target.setPassword(passwordEncoder.encode(newPassword));

        if (userMapper.updateById(target) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "密码重置失败"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "密码已重置");
        return result;
    }

    /**
     * 删除用户。
     *
     * DELETE /api/admin/users/{id}
     */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        User target = requireUser(id);
        User currentAdmin = dataScopeService.getCurrentUser();

        if (currentAdmin.getId().equals(id)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "不能删除当前登录管理员账号"
            );
        }

        // 先解除设备绑定，设备和历史数据本身保留。
        deviceMapper.update(
                null,
                new LambdaUpdateWrapper<Device>()
                        .eq(Device::getUserId, id)
                        .set(Device::getUserId, null)
        );

        if (userMapper.deleteById(id) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "删除用户失败"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "用户已删除，相关设备已解除绑定");
        result.put("username", target.getUsername());
        return result;
    }

    private User requireUser(Long id) {
        if (id == null) {
            throw badRequest("用户ID不能为空");
        }

        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "用户不存在"
            );
        }
        return user;
    }

    private void validateUsername(String username, Long ignoreId) {
        if (!StringUtils.hasText(username)) {
            throw badRequest("用户名不能为空");
        }

        username = username.trim();
        if (username.length() < 3 || username.length() > 32) {
            throw badRequest("用户名长度应为3到32个字符");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);

        if (ignoreId != null) {
            wrapper.ne(User::getId, ignoreId);
        }

        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "用户名已存在"
            );
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw badRequest("密码不能为空");
        }
        if (password.length() < 6 || password.length() > 64) {
            throw badRequest("密码长度应为6到64个字符");
        }
    }

    private void validateRole(String role) {
        if (!"resident".equals(role)
                && !"admin".equals(role)) {
            throw badRequest("角色只能是 resident 或 admin（巡护员以 resident+zone_id 表示）");
        }
    }

    private void validateLocation(Integer building,
                                  Integer floor,
                                  Integer room) {
        if (building == null || building <= 0
                || floor == null || floor <= 0
                || room == null || room <= 0) {
            throw badRequest("学生楼栋、楼层和房间必须为正整数");
        }
    }

    private Map<String, Object> toSafeMap(User user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", user.getId());
        item.put("username", user.getUsername());
        item.put("role", user.getRole());
        item.put("building", user.getBuilding());
        item.put("floor", user.getFloor());
        item.put("room", user.getRoom());
        item.put("phone", user.getPhone());
        item.put("jobNum", user.getJobNum());
        item.put("zoneId", user.getZoneId());
        return item;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer integerValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw badRequest("数字参数格式不正确");
        }
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw badRequest("数字参数格式不正确");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
