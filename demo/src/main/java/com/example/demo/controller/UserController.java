package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.LoginDTO;
import com.example.demo.entity.User;
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
        LoginVO vo = new LoginVO(
                mockToken,
                user.getUsername(),
                user.getRole(),
                user.getPhone(),
                user.getJobNum(),
                user.getId()
        );
        return Result.ok(vo);
    }
}
