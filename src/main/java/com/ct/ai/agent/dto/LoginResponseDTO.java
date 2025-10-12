package com.ct.ai.agent.dto;

import com.ct.ai.agent.vo.UserInfoVO;
import lombok.Data;

@Data
public class LoginResponseDTO {
    private String token;
    private UserInfoVO userInfo;
    private Long expireTime;  // 过期时间（毫秒）
}
