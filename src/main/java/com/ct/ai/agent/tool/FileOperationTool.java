package com.ct.ai.agent.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.ct.ai.agent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * AI 工具：文件操作工具类
 * 基于 Spring AI Tool 注解暴露文件读写能力，供 AI 模型在需要时自动调用
 * 核心功能：读取指定文件内容、写入内容到文件（自动处理目录创建、跨平台路径）
 */
@Component
public class FileOperationTool {

    /**
     * 文件操作根目录（基于全局文件常量，追加 "/file" 子目录隔离文件工具的存储）
     * 使用 Paths.get() 自动适配 Windows(\) 和 Linux/Mac(/) 路径分隔符
     */
    private final String FILE_OPERATION_ROOT_DIR = Paths
            .get(FileConstant.FILE_SAVE_DIR, "file") // 拼接父目录（全局常量）和子目录（file）
            .toString();


    /**
     * AI 工具：读取文件内容
     * 被 Spring AI 识别为可调用工具，AI 可根据用户需求自动触发（如“读取test.txt文件”）
     *
     * @param fileName 待读取的文件名（需包含后缀，如 "report.pdf"、"data.txt"）
     * @return 读取成功：返回文件的 UTF-8 编码内容；读取失败：返回明确的错误信息（含原因）
     */
    @Tool(
            description = "读取指定文件的内容，适用于需要获取本地文件数据的场景",
            name = "readFileTool" // 自定义工具名，便于日志跟踪和工具链管理（可选）
    )
    public String readFile(
            @ToolParam(
                    description = "待读取的文件名（必须包含文件后缀，例如 'test.txt'、'report.pdf'）",
                    required = true
            ) String fileName
    ) {
        // 1. 校验文件名合法性
        if (fileName == null || fileName.trim().isEmpty()) {
            return "读取失败：文件名不能为空，请提供包含后缀的完整文件名（如 'test.txt'）";
        }
        String trimmedFileName = fileName.trim();

        // 2. 构建完整文件路径
        String fullFilePath = Paths.get(FILE_OPERATION_ROOT_DIR, trimmedFileName).toString();

        // 3. 检查文件是否存在（提前拦截“文件不存在”错误，避免抛出异常）
        if (!FileUtil.exist(fullFilePath)) {
            return String.format("读取失败：文件不存在，路径：%s", fullFilePath);
        }

        // 4. 读取文件内容（使用 Hutool 工具类，确保 UTF-8 编码，避免乱码）
        try {
            // 用 IoUtil 读取流并关闭资源（比 FileUtil.readUtf8String 更安全，避免流泄漏）
            return IoUtil.readUtf8(FileUtil.getInputStream(fullFilePath));
        } catch (Exception e) {
            return String.format(
                    "读取失败：文件读取异常，路径：%s，原因：%s",
                    fullFilePath,
                    e.getMessage()
            );
        }
    }


    /**
     * AI 工具：写入内容到文件
     * 被 Spring AI 识别为可调用工具，AI 可根据用户需求自动触发（如“将内容写入result.txt”）
     *
     * @param fileName 待写入的文件名（需包含后缀，如 "output.txt"、"log.md"）
     * @param content  待写入文件的内容（支持文本、JSON、Markdown 等纯文本格式）
     * @return 写入成功：返回成功信息（含文件路径）；写入失败：返回明确的错误信息（含原因）
     */
    @Tool(
            description = "将指定内容写入本地文件，支持纯文本、JSON、Markdown等格式，自动创建不存在的目录",
            name = "writeFileTool"
    )
    public String writeFile(
            @ToolParam(
                    description = "待写入的文件名（必须包含后缀，例如 'output.txt'、'log.md'）",
                    required = true
            ) String fileName,
            @ToolParam(
                    description = "待写入文件的内容（纯文本格式，支持换行、特殊字符）",
                    required = true
            ) String content
    ) {
        // 1. 校验参数合法性（拦截空参数）
        if (fileName == null || fileName.trim().isEmpty()) {
            return "写入失败：文件名不能为空，请提供包含后缀的完整文件名（如 'output.txt'）";
        }
        if (content == null) {
            content = "";
        }
        String trimmedFileName = fileName.trim();

        // 2. 构建完整文件路径
        String fullFilePath = Paths.get(FILE_OPERATION_ROOT_DIR, trimmedFileName).toString();

        // 3. 写入文件（自动创建父目录，确保 UTF-8 编码）
        try {
            // 自动创建不存在的目录（包括多级目录，如 "tmp/file/2024" 不存在时会自动创建）
            FileUtil.mkdir(FILE_OPERATION_ROOT_DIR);
            // 写入内容（覆盖模式：若文件已存在，会覆盖原有内容）
            FileUtil.writeString(content, fullFilePath, StandardCharsets.UTF_8);

            return String.format("写入成功：文件已保存至 %s，写入内容长度：%d 字符",
                    fullFilePath,
                    content.length()
            );
        } catch (Exception e) {
            return String.format(
                    "写入失败：文件写入异常，路径：%s，原因：%s",
                    fullFilePath,
                    e.getMessage()
            );
        }
    }
}