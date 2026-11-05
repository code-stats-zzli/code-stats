// 文件路径: src/main/java/com/omni/business/assistance/questionbank/controller/OcrController.java
package com.omni.business.assistance.questionbank.controller;

import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/questionbank/api")
public class OcrController {

    private static final String TESSDATA_DIR;

    static {
        String defaultTessdataDir = Paths.get(System.getProperty("user.dir"), "tessdata").toString();
        String rawPath = ConfigLoader.getProperty("ocr.tessdata.path", defaultTessdataDir);
        File dir = new File(rawPath);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), rawPath);
        }
        TESSDATA_DIR = dir.getAbsolutePath();

        File chiSim = new File(TESSDATA_DIR, "chi_sim.traineddata");
        System.out.println("[OcrController] tessdata 目录: " + TESSDATA_DIR);
        System.out.println("[OcrController] chi_sim.traineddata 存在: " + chiSim.exists()
                + (chiSim.exists() ? " 大小=" + chiSim.length() + "B" : ""));
    }

    @RequestMapping("/ocr")
    @ResponseBody
    public String ocr() {
        Map<String, Object> result = new HashMap<>();
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) {
            result.put("error", "无法获取请求对象");
            return JsonUtil.toJson(result);
        }

        MultipartResolver resolver = request.getMultipartResolver();
        if (resolver == null) {
            result.put("error", "请求不是 multipart 类型");
            return JsonUtil.toJson(result);
        }

        FileItem fileItem = resolver.getFileItem("image");
        if (fileItem == null) {
            result.put("error", "缺少图片文件");
            return JsonUtil.toJson(result);
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ocr_", ".png");
            fileItem.transferTo(tempFile);
            System.out.println("[OCR] 临时图片已保存: " + tempFile + ", 大小: " + Files.size(tempFile) + " 字节");

            BufferedImage image = ImageIO.read(tempFile.toFile());
            if (image == null) {
                result.put("error", "无法解析图片，文件可能已损坏或不是 PNG 格式");
                return JsonUtil.toJson(result);
            }
            System.out.println("[OCR] 图片读取成功，尺寸: " + image.getWidth() + "x" + image.getHeight());

            ITesseract tesseract = new Tesseract();
            // ★ 直接传入包含 traineddata 的目录
            tesseract.setDatapath(TESSDATA_DIR);
            tesseract.setLanguage("chi_sim+eng");
            String text = tesseract.doOCR(image);
            text = text.replaceAll("\\s+", " ").trim();
            result.put("text", text);
            return JsonUtil.toJson(result);

        } catch (TesseractException e) {
            result.put("error", "OCR识别失败: " + e.getMessage());
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "处理图片失败: " + e.getMessage());
            return JsonUtil.toJson(result);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }
}