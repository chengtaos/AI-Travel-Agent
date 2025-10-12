package com.ct.ai.agent.constant;

import org.springframework.stereotype.Component;

/**
 * 文件常量
 */
@Component
public final class FileConstant {
    /**
     * 文件保存目录
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
