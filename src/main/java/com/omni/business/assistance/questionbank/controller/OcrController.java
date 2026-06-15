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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/questionbank/api")
public class OcrController {

    // 训练数据路径，根据实际部署修改
    private static String TESSDATA_PATH = ConfigLoader.getProperty("ocr.tessdata.path",
            System.getProperty("user.dir") + "/src/main/resources/tessdata");

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

        try (InputStream is = fileItem.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                result.put("error", "无法解析图片");
                return JsonUtil.toJson(result);
            }

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(TESSDATA_PATH);
            tesseract.setLanguage("chi_sim+eng");  // 中文简体 + 英文
            // 可选的图像预处理：二值化、缩放等（可根据需要扩展）
            String text = tesseract.doOCR(image);
            // 清理多余空白和换行
            text = text.replaceAll("\\s+", " ").trim();
            result.put("text", text);
            return JsonUtil.toJson(result);
        } catch (TesseractException e) {
            result.put("error", "OCR识别失败: " + e.getMessage());
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            result.put("error", "处理图片失败: " + e.getMessage());
            return JsonUtil.toJson(result);
        }
    }
}