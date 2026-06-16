package com.omni.business.assistance.screenshot.service;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.framework.spring.context.SpringContextHolder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public class ScreenshotService {

    private final OcrService ocrService;
    private QuestionBankService questionBankService;

    public ScreenshotService() {
        this.ocrService = new OcrService();
        // 从 Spring 容器获取已有的 QuestionBankService（若容器已启动）
        try {
            this.questionBankService = SpringContextHolder.getApplicationContext()
                    .getBeanFactory().getBean(QuestionBankService.class);
        } catch (Exception e) {
            // 若容器未启动，则直接 new（需确保配置已加载）
            this.questionBankService = new QuestionBankService();
        }
    }

    public ScreenshotResult captureAndSearch(Rectangle rect) {
        ScreenshotResult result = new ScreenshotResult();
        try {
            // 1. 截图
            Robot robot = new Robot();
            BufferedImage image = robot.createScreenCapture(rect);
            result.setImage(image);

            // 2. OCR
            String text = ocrService.recognize(image);
            result.setRecognizedText(text);

            // 3. 搜索题库
            if (text != null && !text.trim().isEmpty()) {
                Map<String, Object> searchResult = questionBankService.searchByKeyword(text);
                if (searchResult != null && searchResult.get("row") != null) {
                    Map<String, String> row = (Map<String, String>) searchResult.get("row");
                    // 尝试拼接所有列作为答案展示
                    StringBuilder question = new StringBuilder();
                    StringBuilder answer = new StringBuilder();
                    for (Map.Entry<String, String> entry : row.entrySet()) {
                        if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                            if (question.length() == 0) question.append(entry.getValue());
                            else answer.append(entry.getValue()).append(" ");
                        }
                    }
                    result.setMatchedQuestion(question.toString());
                    result.setMatchedAnswer(answer.toString().trim());
                    result.setFound(true);
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