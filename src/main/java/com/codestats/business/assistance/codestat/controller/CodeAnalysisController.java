// 文件路径: com.demo.business.assistance.codestat.controller.CodeAnalysisController

package com.codestats.business.assistance.codestat.controller;

import com.codestats.business.assistance.codestat.service.CodeAnalysisService;
import com.codestats.common.util.ParamUtils;
import com.framework.spring.annotation.Autowired;
import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;
import com.framework.spring.mvc.ResponseContextHolder;
import com.framework.tomcat.connector.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@Controller
@RequestMapping("/code/api")
public class CodeAnalysisController {

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    /**
     * 返回默认配置
     * GET /demo/code/api/config
     */
    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        return codeAnalysisService.getDefaultConfigJson();
    }

    /**
     * 执行代码分析
     * GET /demo/code/api/analyze?...
     */
    @RequestMapping("/analyze")
    @ResponseBody
    public String analyze() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        return codeAnalysisService.analyze(params);
    }
    @RequestMapping("/download")
    public void downloadFile() throws IOException {
        Response response = ResponseContextHolder.getResponse();
        if (response == null) {
            throw new RuntimeException("No response available for download");
        }

        Map<String, String> params = ParamUtils.parseQueryParams();
        // 兼容两种参数名
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

        // 1. Base64 解码
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

        // 2. URL 解码
        String rawPath;
        try {
            rawPath = URLDecoder.decode(decodedPath, "UTF-8");
        } catch (Exception e) {
            response.setStatus(400, "Bad Request");
            response.write("Invalid URL encoding");
            response.send();
            return;
        }

        // 3. 路径标准化与安全检查
        Path requestedPath = Paths.get(rawPath).normalize();
        if (requestedPath.toString().contains("..")) {
            response.setStatus(403, "Forbidden");
            response.write("Access denied: illegal path sequence");
            response.send();
            return;
        }

        // 4. 文件存在性检查
        if (!Files.exists(requestedPath) || Files.isDirectory(requestedPath)) {
            response.setStatus(404, "Not Found");
            response.write("File not found: " + requestedPath);
            response.send();
            return;
        }

        // 5. ★ 处理文件名：支持添加时间戳后缀
        String fileName = requestedPath.getFileName().toString();
        String addTimestampParam = params.get("addTimestamp");
        if ("true".equalsIgnoreCase(addTimestampParam) || true) {
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

        // 6. 设置 Content-Disposition 头，强制浏览器下载并重命名
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentType("application/octet-stream");

        // 7. 发送文件
        try (InputStream in = Files.newInputStream(requestedPath)) {
            response.sendStatic(in, "application/octet-stream", Files.size(requestedPath));
        }
    }
}