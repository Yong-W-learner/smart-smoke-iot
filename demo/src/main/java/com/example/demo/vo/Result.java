package com.example.demo.vo;

import lombok.Data;

/**
 * 统一接口返回结构：code=200 成功，非200 失败
 */
@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.setCode(500);
        r.setMsg(msg);
        r.setData(null);
        return r;
    }

    /** 带自定义业务码的失败返回（401/403/404 等），HTTP 状态仍为 200，与现有前端约定一致。 */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(null);
        return r;
    }
}
