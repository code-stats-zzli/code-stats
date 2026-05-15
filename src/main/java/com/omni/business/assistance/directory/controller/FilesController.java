// ======== D:\work\git\CodeStats\src\main\java\com\codestats\business\assistance\directory\controller\FilesController.java ========
package com.omni.business.assistance.directory.controller;

import com.omni.business.assistance.directory.service.DirectoryService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/files/api")
public class FilesController {

    @Autowired
    private DirectoryService directoryService;

    private String getRootType(Map<String, String> params) {
        String rt = params.get("rootType");
        if (rt == null || rt.isEmpty()) {
            rt = DirectoryService.ROOT_TYPE_LOCAL;
        }
        return rt;
    }

    @RequestMapping("/root")
    @ResponseBody
    public String getRootInfo() throws Exception {
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        return JsonUtil.toJson(directoryService.getRootInfo(rootType));
    }

    @RequestMapping("/tree")
    @ResponseBody
    public String getTree() throws Exception {
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.getOrDefault("path", "");
        boolean withChildren = !"false".equalsIgnoreCase(params.get("withChildren"));
        var node = directoryService.getTreeNode(rootType, path, withChildren);
        return JsonUtil.toJson(node);
    }

    @RequestMapping("/list")
    @ResponseBody
    public String listFiles() throws Exception {
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.getOrDefault("path", "");
        var list = directoryService.listDirectory(rootType, path);
        return JsonUtil.toJson(list);
    }

    @RequestMapping("/read")
    @ResponseBody
    public String readFile() throws Exception {
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null) return errorResponse("缺少path参数");
        String content = directoryService.readFile(rootType, path);
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("path", path);
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/save")
    @ResponseBody
    public String saveFile() throws Exception {
        String body = getRequestBody();
        Map<String, Object> req = parseJson(body);
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String path = (String) req.get("path");
        String content = (String) req.getOrDefault("content", "");
        if (path == null) return errorResponse("缺少path参数");
        directoryService.writeFile(rootType, path, content);
        return successResponse();
    }

    @RequestMapping("/mkdir")
    @ResponseBody
    public String mkdir() throws Exception {
        String body = getRequestBody();
        Map<String, Object> req = parseJson(body);
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String parentPath = (String) req.getOrDefault("parentPath", "");
        String name = (String) req.get("name");
        if (name == null || name.trim().isEmpty()) return errorResponse("目录名不能为空");
        directoryService.createDirectory(rootType, parentPath, name);
        return successResponse();
    }

    @RequestMapping("/createFile")
    @ResponseBody
    public String createFile() throws Exception {
        String body = getRequestBody();
        Map<String, Object> req = parseJson(body);
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String parentPath = (String) req.getOrDefault("parentPath", "");
        String fileName = (String) req.get("fileName");
        if (fileName == null || fileName.trim().isEmpty()) return errorResponse("文件名不能为空");
        directoryService.createFile(rootType, parentPath, fileName);
        return successResponse();
    }

    @RequestMapping("/rename")
    @ResponseBody
    public String rename() throws Exception {
        String body = getRequestBody();
        Map<String, Object> req = parseJson(body);
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String path = (String) req.get("path");
        String newName = (String) req.get("newName");
        if (path == null || newName == null) return errorResponse("缺少参数");
        directoryService.rename(rootType, path, newName);
        return successResponse();
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete() throws Exception {
        String body = getRequestBody();
        Map<String, Object> req = parseJson(body);
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String path = (String) req.get("path");
        if (path == null) return errorResponse("缺少path参数");
        directoryService.deletePath(rootType, path);
        return successResponse();
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() throws Exception {
        String body = getRequestBody();
        Map<String, Object> req = parseJson(body);
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String parentPath = (String) req.getOrDefault("parentPath", "");
        String fileName = (String) req.get("fileName");
        String base64Content = (String) req.get("content");
        if (fileName == null || base64Content == null) return errorResponse("缺少参数");
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Content);
        String content = new String(bytes, StandardCharsets.UTF_8);
        directoryService.writeFile(rootType, parentPath + (parentPath.isEmpty() ? "" : "/") + fileName, content);
        return successResponse();
    }

    // 文件下载（不修改）
    @RequestMapping("/download")
    public void downloadFile() throws Exception {
        HttpServletResponse response = getResponse();
        if (response == null) {
            throw new RuntimeException("No response available");
        }
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null || path.trim().isEmpty()) {
            response.setStatus(400, "Bad Request");
            response.write("Missing parameter: path");
            response.send();
            return;
        }

        try {
            byte[] data = directoryService.readFileBytes(rootType, path);
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            response.setContentType("application/octet-stream");
            response.getRawOutputStream().write(data);
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(500, "Internal Server Error");
            response.write("下载失败: " + e.getMessage());
            response.send();
        }
    }

    // 目录下载为 ZIP（已修复）
    @RequestMapping("/downloadZip")
    public void downloadDirectoryAsZip() throws Exception {
        HttpServletResponse response = getResponse();
        if (response == null) {
            throw new RuntimeException("No response available");
        }
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null || path.trim().isEmpty()) {
            response.setStatus(400, "Bad Request");
            response.write("Missing parameter: path");
            response.send();
            return;
        }

        // 准备文件名
        String zipName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (zipName.isEmpty()) zipName = "root";
        zipName += ".zip";

        // 设置响应头
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");
        response.setContentType("application/zip");
        response.setStatus(200, "OK");

        // 直接写入输出流，使用 try-catch 避免异常后重复响应
        try {
            directoryService.zipDirectory(rootType, path, response.getRawOutputStream());
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            // 响应头已经发送，无法再返回错误页面，只能记录日志
            e.printStackTrace();
            // 尝试关闭输出流（客户端会断开连接）
            try {
                response.getRawOutputStream().close();
            } catch (IOException ignored) {}
        }
    }

    // ---------- 辅助方法 ----------
    private Map<String, String> parseQueryParams() {
        HttpServletRequest request = RequestContextHolder.getRequest();
        Map<String, String> map = new HashMap<>();
        if (request == null) return map;
        String query = request.getQueryString();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private String getRequestBody() throws IOException {
        HttpServletRequest request = RequestContextHolder.getRequest();
        return request.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                for (String pair : inner.split(",")) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replaceAll("\"", "");
                        String value = kv[1].trim().replaceAll("\"", "");
                        map.put(key, value);
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private HttpServletResponse getResponse() {
        return ResponseContextHolder.getResponse();
    }

    private String successResponse() {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return JsonUtil.toJson(res);
    }

    private String errorResponse(String msg) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("error", msg);
        return JsonUtil.toJson(res);
    }
}