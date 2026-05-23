// 文件路径: com.omni.business.assistance.codestat.controller.CodeAnalysisController
package com.omni.business.assistance.codestat.controller;

import com.omni.business.assistance.codestat.service.CodeAnalysisService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/code/api")
public class CodeAnalysisController {

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    /**
     * 返回默认配置
     * GET /code/api/config
     */
    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        return codeAnalysisService.getDefaultConfigJson();
    }

    /**
     * 执行代码分析（行数统计）
     * GET /code/api/analyze?...
     */
    @RequestMapping("/analyze")
    @ResponseBody
    public String analyze() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        return codeAnalysisService.analyze(params);
    }

    /**
     * 执行依赖分析（类依赖图）
     * GET /code/api/dependencies?rootDir=...&extensions=java&...
     */
    @RequestMapping("/dependencies")
    @ResponseBody
    public String getDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        return codeAnalysisService.analyzeDependencies(params);
    }

    /**
     * 查询某个类的直接依赖（出向）
     * GET /code/api/dependencies/outgoing?className=com.example.A
     */
    @RequestMapping("/dependencies/outgoing")
    @ResponseBody
    public String getOutgoingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) {
            return errorResponse("缺少 className 参数");
        }
        try {
            Map<String, Object> result = codeAnalysisService.getDirectDependencies(className, params);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /**
     * 查询哪些类依赖了某个类（入向）
     * GET /code/api/dependencies/incoming?className=com.example.A
     */
    @RequestMapping("/dependencies/incoming")
    @ResponseBody
    public String getIncomingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) {
            return errorResponse("缺少 className 参数");
        }
        try {
            Map<String, Object> result = codeAnalysisService.getInverseDependencies(className, params);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /**
     * 下载合并文件（原有功能）
     * GET /code/api/download?filePath=...
     */
    @RequestMapping("/download")
    public void downloadFile() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) {
            throw new RuntimeException("No response available for download");
        }

        Map<String, String> params = ParamUtils.parseQueryParams();
        String encodedPath = params.get("path");
        if (encodedPath == null || encodedPath.trim().isEmpty()) {
            encodedPath = params.get("filePath");
        }
        if (encodedPath == null || encodedPath.trim().isEmpty()) {
            response.setStatus(400, "Bad Request");
            response.write("Missing parameter: path or filePath");
            response.send();
            return;
        }

        String decodedPath;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedPath);
            decodedPath = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            response.setStatus(400, "Bad Request");
            response.write("Invalid Base64 encoding for path parameter");
            response.send();
            return;
        }

        String rawPath;
        try {
            rawPath = URLDecoder.decode(decodedPath, "UTF-8");
        } catch (Exception e) {
            response.setStatus(400, "Bad Request");
            response.write("Invalid URL encoding");
            response.send();
            return;
        }

        Path requestedPath = Paths.get(rawPath).normalize();
        if (requestedPath.toString().contains("..")) {
            response.setStatus(403, "Forbidden");
            response.write("Access denied: illegal path sequence");
            response.send();
            return;
        }

        if (!Files.exists(requestedPath) || Files.isDirectory(requestedPath)) {
            response.setStatus(404, "Not Found");
            response.write("File not found: " + requestedPath);
            response.send();
            return;
        }

        String fileName = requestedPath.getFileName().toString();
        String addTimestampParam = params.get("addTimestamp");
        if ("true".equalsIgnoreCase(addTimestampParam) || addTimestampParam == null) {
            String baseName = fileName;
            String ext = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) {
                baseName = fileName.substring(0, dotIdx);
                ext = fileName.substring(dotIdx);
            }
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            fileName = baseName + "_" + timestamp + ext;
        }

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentType("application/octet-stream");

        try (InputStream in = Files.newInputStream(requestedPath)) {
            response.sendStatic(in, "application/octet-stream", Files.size(requestedPath));
        }
    }

    private String errorResponse(String msg) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("error", msg);
        return JsonUtil.toJson(res);
    }
}