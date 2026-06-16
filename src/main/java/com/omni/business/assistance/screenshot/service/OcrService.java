package com.omni.business.assistance.screenshot.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;

public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final ITesseract tesseract;

    public OcrService() {
        String tessdataPath = ConfigLoader.getProperty("ocr.tessdata.path",
                System.getProperty("user.dir") + "/src/main/resources/tessdata");
        log.info("初始化 OCR 服务，tessdata 路径: {}", tessdataPath);
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessdataPath);
        this.tesseract.setLanguage("chi_sim+eng");
        log.info("OCR 语言设置为: chi_sim+eng");
    }

    public String recognize(BufferedImage image) throws TesseractException {
        if (image == null) {
            log.warn("传入的图片为 null，无法识别");
            return "";
        }
        long start = System.currentTimeMillis();
        String text = tesseract.doOCR(image);
        long elapsed = System.currentTimeMillis() - start;
        String cleaned = text.replaceAll("\\s+", " ").trim();
        log.info("OCR 识别完成，耗时 {} ms，原始文本长度: {}，清理后长度: {}",
                elapsed, text.length(), cleaned.length());
        return cleaned;
    }
}