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
     * 允许下载的根目录白名单：user.dir + java.io.tmpdir。
     * 如需扩大到其他目录，在 application.properties 中配置
     * code.analysis.download.allowed-roots=D:/a,D:/b 即可。
     */
    private static Path[] allowedRoots() {
        String extra = com.omni.framework.spring.config.ConfigLoader
                .getProperty("code.analysis.download.allowed-roots", "");
        java.util.List<Path> roots = new java.util.ArrayList<>();
        roots.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize());
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.isEmpty()) {
            roots.add(Paths.get(tmp).toAbsolutePath().normalize());
        }
        if (!extra.isEmpty()) {
            for (String s : extra.split(",")) {
                if (!s.trim().isEmpty()) {
                    roots.add(Paths.get(s.trim()).toAbsolutePath().normalize());
                }
            }
        }
        return roots.toArray(new Path[0]);
    }

    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        return codeAnalysisService.getDefaultConfigJson();
    }

    @RequestMapping("/analyze")
    @ResponseBody
    public String analyze() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        return codeAnalysisService.analyze(params);
    }

    @RequestMapping("/dependencies")
    @ResponseBody
    public String getDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        return codeAnalysisService.analyzeDependencies(params);
    }

    @RequestMapping("/dependencies/outgoing")
    @ResponseBody
    public String getOutgoingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) {
            return errorResponse("缺少 className 参数");
        }
        try {
            return JsonUtil.toJson(codeAnalysisService.getDirectDependencies(className, params));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @RequestMapping("/dependencies/incoming")
    @ResponseBody
    public String getIncomingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) {
            return errorResponse("缺少 className 参数");
        }
        try {
            return JsonUtil.toJson(codeAnalysisService.getInverseDependencies(className, params));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /**
     * 下载合并文件。
     * 修复：原实现先 normalize() 再 contains("..")，normalize 后几乎不可能包含 ".."，
     *      导致任意文件可读。改为“白名单根目录 startsWith 校验”。
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

        // ★ [P0] 白名单校验
        Path requestedPath;
        try {
            requestedPath = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            response.setStatus(400, "Bad Request");
            response.write("Invalid path: " + e.getMessage());
            response.send();
            return;
        }
        boolean allowed = false;
        for (Path root : allowedRoots()) {
            if (requestedPath.startsWith(root)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            response.setStatus(403, "Forbidden");
            response.write("Access denied: path outside allowed roots. Configure 'code.analysis.download.allowed-roots' if needed.");
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

    /** ★ [P0] 统一错误格式：始终返回 JSON 结构 */
    private String errorResponse(String msg) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("error", msg == null ? "未知错误" : msg);
        return JsonUtil.toJson(res);
    }
}