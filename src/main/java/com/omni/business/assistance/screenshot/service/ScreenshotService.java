package com.omni.business.assistance.screenshot.service;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.spring.config.ConfigLoader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    private final OcrService ocrService;
    private QuestionBankService questionBankService;

    // 截图保存目录（懒加载）
    private volatile File saveDirectory;

    public ScreenshotService() {
        this.ocrService = new OcrService();
        try {
            this.questionBankService = SpringContextHolder.getApplicationContext()
                    .getBeanFactory().getBean(QuestionBankService.class);
            log.info("成功注入 QuestionBankService");
        } catch (Exception e) {
            log.warn("无法从 Spring 获取 QuestionBankService，将创建新实例", e);
            this.questionBankService = new QuestionBankService();
        }
    }

    /**
     * 获取截图保存目录（支持配置）
     * 优先级：1. 系统属性/配置文件 screenshot.save.dir
     *         2. 题库根目录（questionbank.default.rootDir）下的 screenshots 子目录
     *         3. 用户主目录下的 screenshots
     */
    public File getSaveDirectory() {
        if (saveDirectory != null && saveDirectory.exists()) {
            return saveDirectory;
        }
        synchronized (this) {
            if (saveDirectory != null && saveDirectory.exists()) {
                return saveDirectory;
            }
            String dirPath = null;
            // 1. 从配置读取自定义目录
            try {
                dirPath = ConfigLoader.getProperty("screenshot.save.dir");
                if (dirPath != null && !dirPath.trim().isEmpty()) {
                    Path path = Paths.get(dirPath.trim());
                    if (Files.isDirectory(path) || Files.createDirectories(path) != null) {
                        saveDirectory = path.toFile();
                        log.info("使用配置的截图目录: {}", saveDirectory.getAbsolutePath());
                        return saveDirectory;
                    }
                }
            } catch (Exception e) {
                log.warn("读取 screenshot.save.dir 失败", e);
            }

            // 2. 尝试题库根目录
            try {
                String qbRoot = questionBankService.getDefaultRootDir();
                if (qbRoot != null && !qbRoot.trim().isEmpty()) {
                    Path qbPath = Paths.get(qbRoot.trim()).resolve("screenshots");
                    if (Files.isDirectory(qbPath) || Files.createDirectories(qbPath) != null) {
                        saveDirectory = qbPath.toFile();
                        log.info("使用题库目录下的 screenshots: {}", saveDirectory.getAbsolutePath());
                        return saveDirectory;
                    }
                }
            } catch (Exception e) {
                log.warn("无法使用题库目录创建截图子目录", e);
            }

            // 3. 回退到用户主目录下的 screenshots
            try {
                Path fallback = Paths.get(System.getProperty("user.home"), "screenshots");
                if (Files.isDirectory(fallback) || Files.createDirectories(fallback) != null) {
                    saveDirectory = fallback.toFile();
                    log.info("使用用户主目录下的 screenshots: {}", saveDirectory.getAbsolutePath());
                    return saveDirectory;
                }
            } catch (Exception e) {
                log.warn("无法创建回退目录", e);
            }

            // 最终保险：当前工作目录
            saveDirectory = new File("screenshots");
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }
            log.info("最终使用当前目录下的 screenshots: {}", saveDirectory.getAbsolutePath());
            return saveDirectory;
        }
    }

    public ScreenshotResult captureAndSearch(Rectangle rect) {
        log.info("开始截图，区域: x={}, y={}, width={}, height={}",
                rect.x, rect.y, rect.width, rect.height);
        ScreenshotResult result = new ScreenshotResult();
        try {
            Robot robot = new Robot();
            BufferedImage image = robot.createScreenCapture(rect);
            result.setImage(image);
            log.debug("截图完成，图片尺寸: {}x{}", image.getWidth(), image.getHeight());

            String text = ocrService.recognize(image);
            result.setRecognizedText(text);
            log.info("OCR 识别文本: '{}'", text);

            if (text != null && !text.trim().isEmpty()) {
                Map<String, Object> preview = questionBankService.getPreviewData(text, 1, 10);
                if (preview != null && preview.containsKey("rows")) {
                    List<Map<String, String>> rows = (List<Map<String, String>>) preview.get("rows");
                    if (rows != null && !rows.isEmpty()) {
                        result.setFound(true);
                        result.setMatchedRows(rows);
                        result.setColumns((List<String>) preview.get("columns"));
                        log.info("题库查询成功，匹配 {} 条记录", rows.size());
                    } else {
                        result.setFound(false);
                        log.info("题库查询无匹配记录");
                    }
                } else {
                    result.setFound(false);
                    log.info("题库查询返回空数据或格式异常");
                }
            } else {
                log.info("OCR 识别结果为空，跳过查询");
            }
        } catch (Exception e) {
            log.error("截图或识别过程发生异常", e);
        }
        return result;
    }
}