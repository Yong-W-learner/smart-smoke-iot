package com.example.demo.dto;

import lombok.Data;

/**
 * 新增森林景区护林员账号入参
 */
@Data
public class AddAdminDTO {
    private String username; // 用户名
    private String jobNum;   // 工号
    private String phone;    // 联系方式（11位手机号）
    private String password; // 密码
    private String role;     // 角色固定为 ranger（缺省同样使用 ranger）
}
