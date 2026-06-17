package com.omni.business.assistance.screenshot.service;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.context.SpringContextHolder;

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

            saveDirectory = new File("screenshots");
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }
            log.info("最终使用当前目录下的 screenshots: {}", saveDirectory.getAbsolutePath());
            return saveDirectory;
        }
    }

    /**
     * OCR 识别并搜索
     */
    public ScreenshotResult captureAndSearchWithImage(BufferedImage image) {
        ScreenshotResult result = new ScreenshotResult();
        result.setImage(image);
        try {
            String text = ocrService.recognize(image);
            result.setRecognizedText(text);
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
                    log.info("题库查询返回空数据");
                }
            } else {
                result.setFound(false);
                log.info("OCR 识别结果为空");
            }
        } catch (Exception e) {
            log.error("识别异常", e);
            result.setFound(false);
            result.setRecognizedText("识别异常: " + e.getMessage());
        }
        return result;
    }

    /**
     * 截取屏幕区域并识别
     */
    public ScreenshotResult captureAndSearch(Rectangle rect) {
        ScreenshotResult result = new ScreenshotResult();
        try {
            Robot robot = new Robot();
            BufferedImage image = robot.createScreenCapture(rect);
            return captureAndSearchWithImage(image);
        } catch (Exception e) {
            log.error("截图或识别异常", e);
            result.setFound(false);
            result.setRecognizedText("异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 直接按关键词搜索题库（不依赖截图）
     */
    public List<Map<String, String>> searchByText(String keyword) {
        try {
            Map<String, Object> result = questionBankService.getPreviewData(keyword, 1, 200);
            if (result != null && result.containsKey("rows")) {
                return (List<Map<String, String>>) result.get("rows");
            }
        } catch (Exception e) {
            log.error("搜索题库失败", e);
        }
        return null;
    }
}