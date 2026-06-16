package com.omni.business.assistance.screenshot.service;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.framework.spring.context.SpringContextHolder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

public class ScreenshotService {

    private final OcrService ocrService;
    private QuestionBankService questionBankService;

    public ScreenshotService() {
        this.ocrService = new OcrService();
        try {
            this.questionBankService = SpringContextHolder.getApplicationContext()
                    .getBeanFactory().getBean(QuestionBankService.class);
        } catch (Exception e) {
            this.questionBankService = new QuestionBankService();
        }
    }

    public ScreenshotResult captureAndSearch(Rectangle rect) {
        ScreenshotResult result = new ScreenshotResult();
        try {
            Robot robot = new Robot();
            BufferedImage image = robot.createScreenCapture(rect);
            result.setImage(image);

            String text = ocrService.recognize(image);
            result.setRecognizedText(text);

            if (text != null && !text.trim().isEmpty()) {
                // 获取前 10 条匹配结果（分页，第1页，每页10条）
                Map<String, Object> preview = questionBankService.getPreviewData(text, 1, 10);
                if (preview != null && preview.containsKey("rows")) {
                    List<Map<String, String>> rows = (List<Map<String, String>>) preview.get("rows");
                    if (rows != null && !rows.isEmpty()) {
                        result.setFound(true);
                        result.setMatchedRows(rows);
                        result.setColumns((List<String>) preview.get("columns"));
                    } else {
                        result.setFound(false);
                    }
                } else {
                    result.setFound(false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}