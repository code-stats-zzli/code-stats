package com.omni.business.assistance.screenshot.service;

import com.omni.framework.spring.config.ConfigLoader;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;

public class OcrService {
    private final ITesseract tesseract;

    public OcrService() {
        String tessdataPath = ConfigLoader.getProperty("ocr.tessdata.path",
                System.getProperty("user.dir") + "/src/main/resources/tessdata");
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessdataPath);
        this.tesseract.setLanguage("chi_sim+eng");
    }

    public String recognize(BufferedImage image) throws TesseractException {
        if (image == null) return "";
        String text = tesseract.doOCR(image);
        return text.replaceAll("\\s+", " ").trim();
    }
}