package com.ct.ai.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoVO {
    private String username;  // 用户名（唯一）
    private String password;  // 加密存储的密码
    private String phone;     // 手机号（可选，用于验证码登录）
    private String email;     // 邮箱（可选）
    private Integer status;   // 账号状态（0-正常，1-禁用）
}
