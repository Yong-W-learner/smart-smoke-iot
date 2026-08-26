package com.example.demo.controller;

import com.example.demo.dto.LoginDTO;
import com.example.demo.dto.RegisterDTO;
import com.example.demo.service.AuthService;
import com.example.demo.vo.LoginVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;


    public AuthController(
            AuthService authService) {

        this.authService =
                authService;
    }


    /**
     * ==========================================
     * 用户登录
     * ==========================================
     *
     * POST /api/auth/login
     *
     * resident / admin 共用。
     */
    @PostMapping("/login")
    public LoginVO login(
            @RequestBody LoginDTO dto) {

        return authService.login(
                dto
        );
    }


    /**
     * ==========================================
     * 居民注册
     * ==========================================
     *
     * POST /api/auth/register
     *
     * 只能注册 resident。
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @RequestBody RegisterDTO dto) {

        authService.register(
                dto
        );


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "success",
                true
        );


        result.put(
                "message",
                "注册成功"
        );


        return result;
    }


    /**
     * ==========================================
     * 当前登录用户
     * ==========================================
     *
     * GET /api/auth/me
     *
     * 必须携带：
     *
     * Authorization: Bearer JWT
     */
    @GetMapping("/me")
    public Map<String, Object> me() {

        return authService
                .getCurrentUser();
    }
}