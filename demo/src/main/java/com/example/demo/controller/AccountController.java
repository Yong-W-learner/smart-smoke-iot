package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.AddAdminDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.vo.Result;
import com.example.demo.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 护林员账号管理接口
 */
@RestController
@RequestMapping("/api")
public class AccountController {

    @Autowired
    private UserMapper userMapper;

    // 全部账号列表（不含密码）
    @GetMapping("/user/list")
    public List<UserVO> list() {
        List<User> users = userMapper.selectList(null);
        List<UserVO> result = new ArrayList<>();
        for (User u : users) {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRole(u.getRole());
            vo.setBuilding(u.getBuilding());
            vo.setFloor(u.getFloor());
            vo.setRoom(u.getRoom());
            vo.setPhone(u.getPhone());
            vo.setJobNum(u.getJobNum());
            result.add(vo);
        }
        return result;
    }

    // 森林景区仅新增护林员账号
    @PostMapping("/user/add")
    public Result<String> add(@RequestBody AddAdminDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isEmpty()) {
            return Result.fail("用户名不能为空");
        }
        if (dto.getJobNum() == null || dto.getJobNum().isEmpty()) {
            return Result.fail("工号不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            return Result.fail("密码不能为空");
        }
        if (dto.getPhone() == null || !dto.getPhone().matches("\\d{11}")) {
            return Result.fail("联系方式需为11位手机号");
        }

        String role = (dto.getRole() == null || dto.getRole().isEmpty()) ? "ranger" : dto.getRole();
        if (!"ranger".equals(role)) {
            return Result.fail("森林景区仅允许创建护林员账号");
        }

        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        w.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectOne(w) != null) {
            return Result.fail("用户名已存在");
        }

        User u = new User();
        u.setUsername(dto.getUsername());
        u.setRole(role);
        u.setJobNum(dto.getJobNum());
        u.setPhone(dto.getPhone());
        u.setPassword(dto.getPassword());
        userMapper.insert(u);
        return Result.ok("新增护林员成功");
    }

    // 删除护林员账号
    @DeleteMapping("/user/{id}")
    public Result<String> delete(@PathVariable Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return Result.fail("账号不存在");
        }
        userMapper.deleteById(id);
        return Result.ok("删除成功");
    }
}
