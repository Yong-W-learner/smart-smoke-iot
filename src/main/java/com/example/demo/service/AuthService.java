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
         *
         * 森林巡护员内部复用 resident 角色（以 zone_id 区分巡护区域），
         * 不再单独开放 patrol 角色，避免角色体系膨胀。
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
         * ==========================================
         * 自助注册已关闭
         * ==========================================
         *
         * 森林巡护场景下，账号由管理员在指挥台统一创建，
         * 不再开放匿名自助注册（防止非授权账号进入巡护端）。
         */
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "系统已关闭自助注册，请由管理员在指挥台创建账号"
        );
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


        result.put(
                "zoneId",
                user.getZoneId()
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