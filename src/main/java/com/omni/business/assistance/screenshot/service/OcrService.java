// 文件路径: src/main/java/com/omni/business/assistance/screenshot/service/OcrService.java
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
import java.nio.file.Paths;

public class OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private final ITesseract tesseract;
    private final String tessdataDir;

    public OcrService() {
        // 默认 tessdata 目录 = 项目根目录/tessdata
        String defaultTessdataDir = Paths.get(System.getProperty("user.dir"), "tessdata").toString();
        // 允许 application.properties 中 ocr.tessdata.path 覆盖（值指向 tessdata 目录本身）
        String rawPath = ConfigLoader.getProperty("ocr.tessdata.path", defaultTessdataDir);

        File dir = new File(rawPath);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), rawPath);
        }
        this.tessdataDir = dir.getAbsolutePath();

        log.info("OCR 初始化：");
        log.info("  tessdata 目录 : {}", this.tessdataDir);

        File chiSim = new File(this.tessdataDir, "chi_sim.traineddata");
        if (!chiSim.exists()) {
            log.error("❌ 未找到语言包: {}", chiSim.getAbsolutePath());
            log.error("   下载地址: https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata");
        } else {
            log.info("  ✅ 语言包 chi_sim.traineddata 大小: {} bytes", chiSim.length());
        }

        this.tesseract = new Tesseract();
        // ★ Tess4J 5.7.0：setDatapath 直接传入包含 traineddata 的目录
        this.tesseract.setDatapath(this.tessdataDir);
        this.tesseract.setLanguage("chi_sim");
        this.tesseract.setTessVariable("textord_min_xheight", "10");
        log.info("  ✅ Tesseract 已就绪，模型: chi_sim（datapath={}）", this.tessdataDir);
    }

    private BufferedImage preprocessImage(BufferedImage src) {
        if (src == null) return null;

        int width = src.getWidth() * 3;
        int height = src.getHeight() * 3;
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, width, height, null);
        g2d.dispose();

        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gGray = gray.createGraphics();
        gGray.drawImage(scaled, 0, 0, null);
        gGray.dispose();

        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D gBinary = binary.createGraphics();
        gBinary.drawImage(gray, 0, 0, null);
        gBinary.dispose();

        return sharpenImage(binary);
    }

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

        BufferedImage processed = preprocessImage(image);
        if (processed == null) processed = image;

        long start = System.currentTimeMillis();
        String text;
        try {
            text = tesseract.doOCR(processed);
        } catch (TesseractException e) {
            log.error("OCR 识别异常，尝试 chi_sim+eng 回退", e);
            this.tesseract.setLanguage("chi_sim+eng");
            try {
                text = tesseract.doOCR(processed);
            } finally {
                this.tesseract.setLanguage("chi_sim");
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        String cleaned = text.replaceAll("\\s+", " ").trim();
        log.info("OCR 识别完成，耗时 {} ms，原始长度 {}，清理后长度 {}，内容: {}",
                elapsed, text.length(), cleaned.length(), cleaned);
        return cleaned;
    }
}