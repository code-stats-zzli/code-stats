package com.omni.business.assistance.questionbank.controller;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.io.InputStream;
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

    /**
     * 扫描匹配的 Excel 文件
     * GET /questionbank/api/scan?rootDir=...&keyword=...
     */
    @RequestMapping("/scan")
    @ResponseBody
    public String scanFiles(String rootDir, String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> files = service.scanExcelFiles(rootDir, keyword);
            result.put("success", true);
            result.put("files", files);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 合并 Excel 文件
     * POST /questionbank/api/merge
     * Body: { "rootDir": "...", "keyword": "...", "outputFile": "..." }
     */
    @RequestMapping("/merge")
    @ResponseBody
    public String mergeFiles(@RequestBody Map<String, String> req) {
        String rootDir = req.get("rootDir");
        String keyword = req.get("keyword");
        String outputFile = req.get("outputFile");
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> mergeResult = service.mergeExcelFiles(rootDir, keyword, outputFile);
            result.put("success", true);
            result.putAll(mergeResult);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 按题目关键词搜索
     * GET /questionbank/api/search?keyword=xxx
     */
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

    @RequestMapping("/download")
    public void downloadMergedFile(String file, HttpServletResponse response) throws Exception {
        Path path = Paths.get(file).normalize();
        if (!Files.exists(path) || Files.isDirectory(path)) {
            response.setStatus(404,"文件不存在");
            response.write("文件不存在");
            response.send();
            return;
        }
        response.setHeader("Content-Disposition", "attachment; filename=\"" + path.getFileName().toString() + "\"");
        response.setContentType("text/plain");
        try (InputStream in = Files.newInputStream(path)) {
            response.sendStatic(in, "text/plain", Files.size(path));
        }
    }
}