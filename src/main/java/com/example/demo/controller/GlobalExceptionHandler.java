package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一接口错误格式，方便前端稳定读取 message。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException e) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", e.getReason() == null ? "请求失败" : e.getReason());

        return ResponseEntity
                .status(e.getStatus())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("接口处理发生未捕获异常", e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "服务器处理失败，请稍后重试");

        return ResponseEntity
                .internalServerError()
                .body(body);
    }
}
