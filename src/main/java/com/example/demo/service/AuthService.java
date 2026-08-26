package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.LoginDTO;
import com.example.demo.dto.RegisterDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.security.JwtUtil;
import com.example.demo.vo.LoginVO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;


    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {

        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }


    /**
     * ==========================================
     * 用户登录
     * ==========================================
     *
     * resident / admin 共用登录接口。
     */
    public LoginVO login(LoginDTO dto) {

        /*
         * 1. 参数检查
         */
        if (dto == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "登录信息不能为空"
            );
        }


        String username =
                trim(dto.getUsername());

        String password =
                dto.getPassword();


        if (!StringUtils.hasText(username)
                || !StringUtils.hasText(password)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名和密码不能为空"
            );
        }


        /*
         * 2. 根据用户名查询用户
         */
        User user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<User>()
                                .eq(
                                        User::getUsername,
                                        username
                                )
                );


        /*
         * 用户不存在
         */
        if (user == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }


        /*
         * 3. 验证密码
         */
        boolean passwordCorrect =
                verifyPassword(
                        password,
                        user
                );


        if (!passwordCorrect) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }


        /*
         * 4. 检查用户角色
         */
        String role =
                user.getRole();


        if (!"resident".equals(role)
                && !"admin".equals(role)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "账户角色异常"
            );
        }


        /*
         * 5. 生成 JWT
         *
         * JWT 中写入：
         *
         * userId
         * username
         * role
         */
        String token =
                jwtUtil.generateToken(
                        user.getId(),
                        user.getUsername(),
                        user.getRole()
                );


        /*
         * 6. 返回登录信息
         */
        return new LoginVO(
                token,
                user.getRole(),
                user.getUsername()
        );
    }


    /**
     * ==========================================
     * 居民注册
     * ==========================================
     *
     * 注意：
     *
     * 公开注册接口只能注册 resident。
     *
     * 不允许用户通过请求参数
     * 自己注册 admin。
     */
    public void register(RegisterDTO dto) {

        /*
         * 1. 参数检查
         */
        if (dto == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "注册信息不能为空"
            );
        }


        String username =
                trim(dto.getUsername());

        String password =
                dto.getPassword();

        String phone =
                trim(dto.getPhone());


        /*
         * 用户名
         */
        if (!StringUtils.hasText(username)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名不能为空"
            );
        }


        if (username.length() < 3
                || username.length() > 32) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名长度应为3到32个字符"
            );
        }


        /*
         * 密码
         */
        if (!StringUtils.hasText(password)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "密码不能为空"
            );
        }


        if (password.length() < 6
                || password.length() > 64) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "密码长度应为6到64个字符"
            );
        }


        /*
         * 手机号
         */
        if (!StringUtils.hasText(phone)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "手机号不能为空"
            );
        }


        /*
         * 居民必须绑定房间
         */
        if (dto.getBuilding() == null
                || dto.getBuilding() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "楼栋信息不正确"
            );
        }


        if (dto.getFloor() == null
                || dto.getFloor() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "楼层信息不正确"
            );
        }


        if (dto.getRoom() == null
                || dto.getRoom() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间信息不正确"
            );
        }


        /*
         * 2. 判断用户名是否重复
         */
        Long count =
                userMapper.selectCount(
                        new LambdaQueryWrapper<User>()
                                .eq(
                                        User::getUsername,
                                        username
                                )
                );


        if (count != null
                && count > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "用户名已存在"
            );
        }


        /*
         * 3. 创建用户
         */
        User user =
                new User();


        user.setUsername(
                username
        );


        /*
         * 强制居民角色
         */
        user.setRole(
                "resident"
        );


        user.setBuilding(
                dto.getBuilding()
        );


        user.setFloor(
                dto.getFloor()
        );


        user.setRoom(
                dto.getRoom()
        );


        user.setPhone(
                phone
        );


        /*
         * 居民没有管理员工号
         */
        user.setJobNum(
                null
        );


        /*
         * BCrypt 加密密码
         *
         * 数据库不会保存：
         *
         * 123456
         *
         * 而保存：
         *
         * $2a$10$......
         */
        user.setPassword(
                passwordEncoder.encode(
                        password
                )
        );


        int rows =
                userMapper.insert(
                        user
                );


        if (rows != 1) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "注册失败，请稍后重试"
            );
        }
    }


    /**
     * ==========================================
     * 获取当前登录用户
     * ==========================================
     *
     * JWT 经过 JwtAuthenticationFilter 后，
     * 用户身份会放入：
     *
     * SecurityContextHolder
     */
    public Map<String, Object> getCurrentUser() {

        /*
         * 获取当前认证信息
         */
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();


        /*
         * 未登录
         */
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }


        /*
         * JwtAuthenticationFilter 中
         * authentication 的 name
         * 就是 username。
         */
        String username =
                authentication.getName();


        /*
         * 根据用户名查询数据库
         */
        User user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<User>()
                                .eq(
                                        User::getUsername,
                                        username
                                )
                );


        if (user == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在"
            );
        }


        /*
         * ======================================
         * 返回当前用户信息
         * ======================================
         *
         * 注意：
         *
         * 绝对不返回 password。
         */
        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "id",
                user.getId()
        );


        result.put(
                "username",
                user.getUsername()
        );


        result.put(
                "role",
                user.getRole()
        );


        result.put(
                "building",
                user.getBuilding()
        );


        result.put(
                "floor",
                user.getFloor()
        );


        result.put(
                "room",
                user.getRoom()
        );


        result.put(
                "phone",
                user.getPhone()
        );


        result.put(
                "jobNum",
                user.getJobNum()
        );


        return result;
    }


    /**
     * ==========================================
     * 密码验证
     * ==========================================
     *
     * 同时兼容：
     *
     * 旧明文密码
     * BCrypt密码
     */
    private boolean verifyPassword(
            String rawPassword,
            User user) {

        String storedPassword =
                user.getPassword();


        if (!StringUtils.hasText(
                storedPassword
        )) {

            return false;
        }


        /*
         * ======================================
         * BCrypt密码
         * ======================================
         */
        if (isBcryptPassword(
                storedPassword
        )) {

            return passwordEncoder.matches(
                    rawPassword,
                    storedPassword
            );
        }


        /*
         * ======================================
         * 兼容旧明文密码
         * ======================================
         *
         * 如果数据库以前存的是：
         *
         * 123456
         *
         * 那么第一次成功登录时，
         * 自动升级成 BCrypt。
         */
        if (!storedPassword.equals(
                rawPassword
        )) {

            return false;
        }


        /*
         * 自动升级旧密码
         */
        String encodedPassword =
                passwordEncoder.encode(
                        rawPassword
                );


        user.setPassword(
                encodedPassword
        );


        userMapper.updateById(
                user
        );


        return true;
    }


    /**
     * 判断密码是否是 BCrypt 格式
     */
    private boolean isBcryptPassword(
            String password) {

        return password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$");
    }


    /**
     * 去掉字符串首尾空格
     */
    private String trim(
            String value) {

        return value == null
                ? null
                : value.trim();
    }
}