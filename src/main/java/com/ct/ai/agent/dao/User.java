package com.ct.ai.agent.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;  // 用户名（唯一）
    private String password;  // 加密存储的密码
    private String phone;     // 手机号（可选，用于验证码登录）
    private String email;     // 邮箱（可选）
    private Integer status;   // 账号状态（0-正常，1-禁用）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
