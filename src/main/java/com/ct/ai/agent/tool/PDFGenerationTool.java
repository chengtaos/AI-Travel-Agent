package com.ct.ai.agent.tool;

import cn.hutool.core.io.FileUtil;
import com.ct.ai.agent.constant.FileConstant;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.IOException;

/**
 * PDF生成工具（基于iTextPDF实现）
 * 核心功能：根据输入的内容和元数据，生成包含中文支持、页码的PDF文件，自动管理文件目录
 */
@Slf4j // 自动注入日志对象
public class PDFGenerationTool {

    // 固定配置：PDF页码字体大小、位置参数（避免硬编码分散）
    private static final float PAGE_NUM_FONT_SIZE = 10F;
    private static final float PAGE_NUM_X = 500F; // 页码X轴位置
    private static final float PAGE_NUM_Y = 20F;  // 页码Y轴位置
    private static final float PAGE_NUM_WIDTH = 100F; // 页码文本宽度
    // 中文字体配置：iTextPDF内置中文字体（STSongStd-Light）及编码
    private static final String CHINESE_FONT_NAME = "STSongStd-Light";
    private static final String CHINESE_FONT_ENCODING = "UniGB-UCS2-H";

    /**
     * 生成PDF文件
     * 自动创建目标目录，支持中文显示，添加文档元数据（标题、作者）和页码
     *
     * @param fileName PDF文件名（需包含后缀，如"report.pdf"）
     * @param content  PDF正文内容
     * @param title    PDF文档标题（元数据）
     * @param author   PDF文档作者（元数据）
     * @return 生成结果提示（成功返回路径，失败返回错误信息）
     */
    @Tool(description = "生成包含指定内容和元数据的PDF文件，支持中文显示和页码标注", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "PDF文件名（必须包含.pdf后缀，如'user_report.pdf'）", required = true) String fileName,
            @ToolParam(description = "PDF文档的正文内容（支持多行文本）", required = true) String content,
            @ToolParam(description = "PDF文档的标题（将写入文档元数据）", required = true) String title,
            @ToolParam(description = "PDF文档的作者（将写入文档元数据）", required = true) String author) {
        // 1. 入参校验：避免无效输入导致的后续异常
        validateParams(fileName, content, title, author);

        // 2. 构建文件路径：基于常量目录 + 文件名，统一PDF存储位置
        String pdfDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = pdfDir + "/" + fileName;
        log.info("[PDF生成工具] 开始生成PDF，路径：{}，标题：{}，作者：{}", filePath, title, author);

        try {
            // 3. 自动创建目录（ Hutool工具类，不存在则创建，避免IO异常）
            FileUtil.mkdir(pdfDir);

            // 4. 初始化PDF核心对象（try-with-resources自动关闭流，避免资源泄漏）
            try (PdfWriter writer = new PdfWriter(filePath); // PDF写入器（关联目标文件）
                 PdfDocument pdfDoc = new PdfDocument(writer); // PDF文档对象
                 Document layoutDoc = new Document(pdfDoc)) { // 布局文档（处理文本、元素排版）

                // 5. 设置PDF元数据（标题、作者，便于文档管理）
                PdfDocumentInfo docMeta = pdfDoc.getDocumentInfo();
                docMeta.setTitle(title);
                docMeta.setAuthor(author);

                // 6. 配置中文字体（解决iTextPDF默认不支持中文的问题）
                PdfFont chineseFont = PdfFontFactory.createFont(CHINESE_FONT_NAME, CHINESE_FONT_ENCODING);
                layoutDoc.setFont(chineseFont); // 全局设置中文字体

                // 7. 添加正文内容到PDF
                Paragraph contentPara = new Paragraph(content);
                layoutDoc.add(contentPara);
                log.debug("[PDF生成工具] 已添加正文内容，长度：{}字符", content.length());

                // 8. 添加页码（遍历所有页面，在固定位置插入页码）
                addPageNumbers(pdfDoc, layoutDoc, chineseFont);

                // 9. 生成成功：返回路径信息
                String successMsg = "PDF生成成功，存储路径：" + filePath;
                log.info(successMsg);
                return successMsg;

            } catch (IOException e) {
                // 捕获PDF写入/字体加载异常
                String errorMsg = "PDF生成失败（文件操作异常）：" + e.getMessage();
                log.error("[PDF生成工具] {}", errorMsg, e); // 打印异常栈，便于排查
                return errorMsg;
            }

        } catch (IllegalArgumentException e) {
            // 捕获入参校验异常
            log.warn("[PDF生成工具] 入参无效：{}", e.getMessage());
            return "PDF生成失败（参数错误）：" + e.getMessage();
        }
    }

    /**
     * 入参校验：确保关键参数非空/格式正确
     */
    private void validateParams(String fileName, String content, String title, String author) {
        // 校验文件名：非空且包含.pdf后缀
        Assert.hasText(fileName, "PDF文件名不能为空");
        Assert.isTrue(fileName.endsWith(".pdf"), "PDF文件名必须以.pdf结尾（如'report.pdf'）");

        // 校验核心内容：非空（避免生成空文档）
        Assert.hasText(content, "PDF正文内容不能为空");
        Assert.hasText(title, "PDF文档标题不能为空");
        Assert.hasText(author, "PDF文档作者不能为空");
    }

    /**
     * 为PDF添加页码（每页底部固定位置显示“Page X of Y”）
     */
    private void addPageNumbers(PdfDocument pdfDoc, Document layoutDoc, PdfFont font) {
        int totalPages = pdfDoc.getNumberOfPages();
        log.debug("[PDF生成工具] 文档总页数：{}，开始添加页码", totalPages);

        // 遍历所有页面，在指定位置插入页码
        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            // 构建页码文本（如"Page 1 of 5"）
            String pageText = String.format("Page %d of %d", pageNum, totalPages);
            // 创建页码段落：设置字体、位置（固定在页面底部）
            Paragraph pageNumPara = new Paragraph(new Text(pageText))
                    .setFont(font)
                    .setFontSize(PAGE_NUM_FONT_SIZE)
                    .setFixedPosition(pageNum, PAGE_NUM_X, PAGE_NUM_Y, PAGE_NUM_WIDTH);

            layoutDoc.add(pageNumPara);
        }
    }
}