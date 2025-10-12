package com.ct.ai.agent.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.ct.ai.agent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.File;

/**
 * 资源下载工具
 * 功能：从指定URL下载文件并保存到本地目录，支持自动创建存储目录，适配AI工具调用场景
 */
@Slf4j
public class ResourceDownloadTool {

    // 下载文件存储根目录（基于常量配置，统一管理文件路径）
    private static final String DOWNLOAD_BASE_DIR = FileConstant.FILE_SAVE_DIR + "/download";

    /**
     * 从指定URL下载资源并保存到本地
     *
     * @param url      资源下载地址（如"https://example.com/file.pdf"）
     * @param fileName 保存的文件名（需包含后缀，如"report.pdf"）
     * @return 下载结果提示（成功返回文件路径，失败返回错误信息）
     */
    @Tool(description = "从指定URL下载资源并保存到本地，支持各类文件格式（需提供完整URL和带后缀的文件名）")
    public String downloadResource(
            @ToolParam(description = "资源的完整下载URL（如https://example.com/data.zip）", required = true) String url,
            @ToolParam(description = "保存的文件名（必须包含后缀，如document.pdf、image.jpg）", required = true) String fileName) {

        // 1. 入参校验：提前拦截无效输入，避免后续操作异常
        validateParams(url, fileName);

        // 2. 构建完整文件路径
        String filePath = DOWNLOAD_BASE_DIR + "/" + fileName;
        log.info("[资源下载工具] 开始下载：URL={}，保存路径={}", url, filePath);

        try {
            // 3. 自动创建存储目录（若不存在）
            FileUtil.mkdir(DOWNLOAD_BASE_DIR);

            // 4. 执行下载
            long fileSize = HttpUtil.downloadFile(url, new File(filePath));

            // 5. 验证下载结果（检查文件是否存在且大小合理）
            if (fileSize > 0 && FileUtil.exist(filePath)) {
                String successMsg = String.format("资源下载成功，保存路径：%s，文件大小：%.2fKB",
                        filePath, fileSize / 1024.0);
                log.info(successMsg);
                return successMsg;
            } else {
                String errorMsg = "下载失败：文件为空或未生成";
                log.warn("[资源下载工具] {}", errorMsg);
                return errorMsg;
            }

        } catch (Exception e) {
            String errorMsg = "下载失败：" + e.getMessage();
            log.error("[资源下载工具] URL={} 下载异常", url, e); // 记录异常栈，便于排查
            return errorMsg;
        }
    }

    /**
     * 入参校验：确保URL和文件名格式合法
     */
    private void validateParams(String url, String fileName) {
        // 校验URL非空且格式合法（简单判断是否包含http/https）
        Assert.hasText(url, "下载URL不能为空");
        Assert.isTrue(url.startsWith("http://") || url.startsWith("https://"),
                "URL格式不合法，必须以http://或https://开头");

        // 校验文件名非空且包含后缀
        Assert.hasText(fileName, "保存的文件名不能为空");
        Assert.isTrue(fileName.contains("."), "文件名必须包含后缀（如.pdf、.txt）");
    }
}