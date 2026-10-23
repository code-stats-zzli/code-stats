// 文件路径: src/main/java/com/omni/business/assistance/screenshot/service/ScreenshotService.java
package com.omni.business.assistance.screenshot.service;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.business.assistance.screenshot.model.ScreenshotResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.context.SpringContextHolder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    public File getSaveDirectory() {
        if (saveDirectory != null && saveDirectory.exists()) {
            return saveDirectory;
        }
        synchronized (this) {
            if (saveDirectory != null && saveDirectory.exists()) {
                return saveDirectory;
            }
            String dirPath = ConfigLoader.getProperty("screenshot.save.dir");
            if (dirPath != null && !dirPath.trim().isEmpty()) {
                try {
                    Path path = Paths.get(dirPath.trim());
                    if (Files.isDirectory(path) || Files.createDirectories(path) != null) {
                        saveDirectory = path.toFile();
                        log.info("使用配置的截图目录: {}", saveDirectory.getAbsolutePath());
                        return saveDirectory;
                    }
                } catch (Exception e) {
                    log.warn("读取 screenshot.save.dir 失败", e);
                }
            }

            try {
                Path fallback = Paths.get(System.getProperty("user.home"), "screenshots");
                if (Files.isDirectory(fallback) || Files.createDirectories(fallback) != null) {
                    saveDirectory = fallback.toFile();
                    log.info("使用默认截图目录（用户主目录）: {}", saveDirectory.getAbsolutePath());
                    return saveDirectory;
                }
            } catch (Exception ignored) {}

            saveDirectory = new File("screenshots");
            if (!saveDirectory.exists()) saveDirectory.mkdirs();
            log.info("最终使用当前目录下的 screenshots: {}", saveDirectory.getAbsolutePath());
            return saveDirectory;
        }
    }

    /**
     * 保存 BufferedImage 到本地目录（保留：供完整浮窗的「保存截图」按钮使用）。
     */
    public File saveImage(BufferedImage image, String prefix) throws Exception {
        File saveDir = getSaveDirectory();
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            throw new Exception("无法创建保存目录: " + saveDir.getAbsolutePath());
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = prefix + "_" + timestamp + ".png";
        File outputFile = new File(saveDir, fileName);
        int counter = 1;
        while (outputFile.exists()) {
            fileName = prefix + "_" + timestamp + "_" + (counter++) + ".png";
            outputFile = new File(saveDir, fileName);
        }
        ImageIO.write(image, "png", outputFile);
        log.info("截图已保存: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 将 BufferedImage 复制到系统剪贴板（作为图片数据）。
     */
    public boolean copyToClipboard(BufferedImage image) {
        if (image == null) {
            log.warn("copyToClipboard: image 为 null");
            return false;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new ImageSelection(image), null);
            log.info("截图已复制到剪贴板: {}x{}", image.getWidth(), image.getHeight());
            return true;
        } catch (Exception e) {
            log.error("复制截图到剪贴板失败", e);
            return false;
        }
    }

    /**
     * ★ 新增：OCR 识别图片中的文字并写入系统剪贴板（文本形式）。
     * 用于「文字识别」功能：选区截图后直接识别，用户 Ctrl+V 即可粘贴文字。
     *
     * @param image 选区截图
     * @return true 表示识别到文字且已成功复制到剪贴板
     */
    public boolean ocrToClipboard(BufferedImage image) {
        if (image == null) {
            log.warn("ocrToClipboard: image 为 null");
            return false;
        }
        try {
            String text = ocrService.recognize(image);
            if (text == null || text.trim().isEmpty()) {
                log.info("OCR 未识别到文字");
                return false;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            log.info("OCR 文字已复制到剪贴板，共 {} 字符", text.length());
            return true;
        } catch (Exception e) {
            log.error("OCR 识别失败", e);
            return false;
        }
    }

    public File copyAndSave(BufferedImage image, String prefix) throws Exception {
        copyToClipboard(image);
        return saveImage(image, prefix);
    }

    private static class ImageSelection implements Transferable {
        private final Image image;
        ImageSelection(Image image) { this.image = image; }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }

    // ============ 以下为占位方法（题库搜索链路，后续接入 QuestionBankService） ============
    public ScreenshotResult captureAndSearchWithImage(BufferedImage image) { return new ScreenshotResult(); }
    public ScreenshotResult captureAndSearch(Rectangle rect) { return new ScreenshotResult(); }
    public List<Map<String, String>> searchByText(String keyword) { return null; }
}