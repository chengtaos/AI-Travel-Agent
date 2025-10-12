package com.ct.ai.agent.dto;

import lombok.Data;

@Data
public class RegisterRequestDTO {
    private String username;
    private String password;
    private String confirmPassword;
    private String phone;
    private String code;  // 手机验证码
}
