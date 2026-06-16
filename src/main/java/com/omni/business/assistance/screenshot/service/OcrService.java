package com.omni.business.assistance.screenshot.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;

public class OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private final ITesseract tesseract;
    private final String tessdataPath;

    public OcrService() {
        this.tessdataPath = ConfigLoader.getProperty("ocr.tessdata.path",
                System.getProperty("user.dir") + "/src/main/resources/tessdata");
        log.info("初始化 OCR 服务，tessdata 路径: {}", tessdataPath);

        File tessdataDir = new File(tessdataPath);
        File chiSimFile = new File(tessdataDir, "chi_sim.traineddata");
        if (!chiSimFile.exists()) {
            log.warn("chi_sim.traineddata 文件不存在于 {}", tessdataPath);
        } else {
            log.info("chi_sim.traineddata 文件存在，大小: {} bytes", chiSimFile.length());
        }

        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessdataPath);
        this.tesseract.setLanguage("chi_sim");
        // 使用自动页面分割，更适合多种布局
        //this.tesseract.setPageSegMode(ITesseract.PAGE_SEPARATOR.);
        // 设置最小文字高度（像素）
        this.tesseract.setTessVariable("textord_min_xheight", "10");
        // 可选：设置字符白名单（中文范围太大，可先不设置，或设置常用字）
        // 这里暂时不限制，避免漏掉生僻字
        log.info("OCR 语言设置为 chi_sim，PSM=AUTO");
        try {
            String version = "";//tesseract.getVersion();
            log.info("Tesseract 版本: {}", version);
        } catch (Exception e) {
            log.warn("无法获取 Tesseract 版本", e);
        }
    }

    /**
     * 图像预处理：灰度化 + 自适应二值化 + 锐化 + 缩放
     */
    private BufferedImage preprocessImage(BufferedImage src) {
        if (src == null) return null;

        // 1. 放大图像（3倍，提高小字体识别率）
        int width = src.getWidth() * 3;
        int height = src.getHeight() * 3;
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, width, height, null);
        g2d.dispose();

        // 2. 灰度化
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gGray = gray.createGraphics();
        gGray.drawImage(scaled, 0, 0, null);
        gGray.dispose();

        // 3. 自适应二值化（使用局部阈值，这里简化：采用全局阈值，但计算最佳阈值）
        // 简单方法：遍历灰度值，计算 Otsu 阈值（但实现稍复杂，这里使用固定阈值 100，可根据图像动态调整）
        // 更好的做法：使用 OpenCV，但避免引入依赖，先试用固定阈值 120
        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D gBinary = binary.createGraphics();
        gBinary.drawImage(gray, 0, 0, null);
        gBinary.dispose();

        // 4. 锐化（增强边缘）
        BufferedImage sharpened = sharpenImage(binary);

        log.debug("图像预处理完成: 原始 {}x{}, 处理后 {}x{}", src.getWidth(), src.getHeight(), width, height);
        return sharpened;
    }

    /**
     * 锐化图像（简单卷积锐化）
     */
    private BufferedImage sharpenImage(BufferedImage src) {
        float[] kernelData = {
                0.0f, -1.0f, 0.0f,
                -1.0f, 5.0f, -1.0f,
                0.0f, -1.0f, 0.0f
        };
        Kernel kernel = new Kernel(3, 3, kernelData);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }

    public String recognize(BufferedImage image) throws TesseractException {
        if (image == null) {
            log.warn("传入的图片为 null，无法识别");
            return "";
        }

        BufferedImage processedImage = preprocessImage(image);
        if (processedImage == null) {
            log.warn("图像预处理失败，使用原始图像");
            processedImage = image;
        }

        long start = System.currentTimeMillis();
        String text;
        try {
            text = tesseract.doOCR(processedImage);
        } catch (TesseractException e) {
            log.error("OCR 识别异常，可能语言包不兼容", e);
            // 尝试使用 chi_sim+eng 作为回退
            log.info("尝试使用 chi_sim+eng 组合语言包...");
            this.tesseract.setLanguage("chi_sim+eng");
            text = tesseract.doOCR(processedImage);
            this.tesseract.setLanguage("chi_sim");
        }
        long elapsed = System.currentTimeMillis() - start;
        String cleaned = text.replaceAll("\\s+", " ").trim();
        log.info("OCR 识别完成，耗时 {} ms，原始文本长度: {}，清理后长度: {}，原始内容: {}",
                elapsed, text.length(), cleaned.length(), text);
        return cleaned;
    }
}