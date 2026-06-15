// 文件路径: src/main/java/com/omni/business/assistance/questionbank/controller/QuestionBankController.java
package com.omni.business.assistance.questionbank.controller;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/questionbank/api")
public class QuestionBankController {

    @Autowired
    private QuestionBankService service;

    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("rootDir", service.getDefaultRootDir());
        config.put("outputFile", service.getDefaultOutputFile());
        config.put("keyword", service.getDefaultKeyword());
        config.put("namePrefixes", service.getDefaultNamePrefixes());
        return JsonUtil.toJson(config);
    }

    @RequestMapping("/scan")
    @ResponseBody
    public String scanFiles(String rootDir, String keyword, String namePrefixes) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> files = service.scanExcelFiles(rootDir, keyword, namePrefixes);
            result.put("success", true);
            result.put("files", files);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/merge")
    @ResponseBody
    public String mergeFiles(@RequestBody Map<String, String> req) {
        String rootDir = req.get("rootDir");
        String keyword = req.get("keyword");
        String namePrefixes = req.get("namePrefixes");
        String outputFile = req.get("outputFile");
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> mergeResult = service.mergeFiles(rootDir, keyword, namePrefixes, outputFile);
            result.put("success", true);
            result.putAll(mergeResult);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/search")
    @ResponseBody
    public String search(String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, String>> matches = service.searchByKeyword(keyword);
            result.put("success", true);
            result.put("matches", matches);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/preview")
    @ResponseBody
    public String previewTable(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = service.getPreviewData(keyword, page, pageSize);
            result.put("success", true);
            result.putAll(data);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/download")
    public void downloadMergedFile(@RequestParam("file") String file, HttpServletResponse response) throws Exception {
        String decodedPath = URLDecoder.decode(file, StandardCharsets.UTF_8.name());
        Path path = Paths.get(decodedPath).normalize();

        if (path.toString().contains("..")) {
            response.setStatus(403, "Access denied");
            response.write("Access denied");
            response.send();
            return;
        }

        if (!Files.exists(path) || Files.isDirectory(path)) {
            response.setStatus(404, "Not Found");
            response.write("File not found: " + path);
            response.send();
            return;
        }

        response.setHeader("Content-Disposition", "attachment; filename=\"" + path.getFileName().toString() + "\"");
        response.setContentType("text/plain");

        try (InputStream in = Files.newInputStream(path)) {
            response.sendStatic(in, "text/plain", Files.size(path));
        } catch (Exception e) {
            response.setStatus(500, "Internal Server Error");
            response.write("Internal error: " + e.getMessage());
            response.send();
        }
    }

    // ========== 导出 Excel ==========
    @RequestMapping("/exportExcel")
    public void exportExcel(@RequestParam(value = "keyword", required = false) String keyword,
                            HttpServletResponse response) throws Exception {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = "题库数据_" + System.currentTimeMillis() + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            service.exportToExcel(keyword, response.getOutputStream());
            // 注意：service.exportToExcel 已经写入输出流，无需再调用 send()
        } catch (Exception e) {
            e.printStackTrace();
            // 修正：setStatus 需要两个参数
            response.setStatus(500, "Internal Server Error");
            response.setContentType("text/plain;charset=UTF-8");
            response.write("导出失败：" + e.getMessage());
            response.send();
        }
    }
}