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
            Map<String, Object> mergeResult = service.mergeExcelFiles(rootDir, keyword, namePrefixes, outputFile);
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

    @RequestMapping("/download")
    public void downloadMergedFile(String file, HttpServletResponse response) throws Exception {
        Path path = Paths.get(file).normalize();
        if (!Files.exists(path) || Files.isDirectory(path)) {
            response.setStatus(404, "文件不存在");
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