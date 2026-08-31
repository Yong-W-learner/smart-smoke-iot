package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.LoginDTO;
import com.example.demo.dto.RegisterDTO;
import com.example.demo.entity.Device;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.vo.LoginVO;
import com.example.demo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    /**
     * 森林景区不开放公众注册，护林员账号由系统预置或统一创建。
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterDTO dto){
        return Result.fail("森林景区不开放账号注册，请使用护林员账号登录");
        /* 旧社区注册逻辑保留在源码历史中，不再进入运行流程。
        // 手机号必须为11位数字
        if (dto.getPhone() == null || !dto.getPhone().matches("\\d{11}")) {
            return Result.fail("手机号需为11位数字");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User exist = userMapper.selectOne(wrapper);
        if(exist != null){
            return Result.fail("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setRole("resident"); //强制居民，不能注册管理员
        user.setBuilding(dto.getBuilding());
        user.setFloor(dto.getFloor());
        user.setRoom(dto.getRoom());
        user.setPhone(dto.getPhone());
        user.setPassword(dto.getPassword());
        user.setJobNum(null);
        userMapper.insert(user);

        // 注册时绑定传感器：优先占用同位置空闲设备（真机），否则新建模拟设备
        bindDeviceForResident(user);

        return Result.ok("注册成功"); */
    }

    /**
     * 森林景区登录接口：仅允许护林员账号。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto){
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        wrapper.eq(User::getPassword, dto.getPassword());
        User user = userMapper.selectOne(wrapper);
        if(user == null){
            return Result.fail("用户名或密码错误");
        }
        if (!"ranger".equals(user.getRole())) {
            return Result.fail("该账号不是护林员账号");
        }
        //TODO 后续替换为真实JWT生成token，现在模拟
        String mockToken = "mock-token-" + user.getUsername();
        Long deviceId = null;
        LoginVO vo = new LoginVO(
                mockToken,
                user.getUsername(),
                user.getRole(),
                user.getBuilding(),
                user.getFloor(),
                user.getRoom(),
                user.getPhone(),
                user.getJobNum(),
                user.getId(),
                deviceId
        );
        return Result.ok(vo);
    }

    /**
     * 为居民绑定/创建一台传感器设备，返回设备编号。
     * 优先级：已有绑定设备 → 认领同位置空闲设备（真机）→ 新建模拟设备。
     */
    private Long bindDeviceForResident(User user) {
        // 按住户位置找本户设备：同一户的所有账号（栋/层/户相同）共用同一台设备
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<>();
        w.eq(Device::getBuilding, user.getBuilding())
         .eq(Device::getFloor, user.getFloor())
         .eq(Device::getRoom, user.getRoom())
         .last("LIMIT 1");
        Device d = deviceMapper.selectOne(w);
        if (d != null) {
            // 记录首绑居民（仅当设备尚未归属时），便于管理员列表展示
            if (d.getUserId() == null) {
                d.setUserId(user.getId());
                deviceMapper.updateById(d);
            }
            return d.getDeviceId();
        }

        // 本户还没有设备，新建一台（真机/模拟，视安装情况而定）
        Device nd = new Device();
        nd.setBuilding(user.getBuilding());
        nd.setFloor(user.getFloor());
        nd.setRoom(user.getRoom());
        nd.setStatus(1); // 模拟设备，视为在线
        nd.setUserId(user.getId());
        deviceMapper.insert(nd);
        return nd.getDeviceId();
    }
}
