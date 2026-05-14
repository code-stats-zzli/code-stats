// ======== D:\work\git\CodeStats\src\main\java\com\codestats\business\assistance\directory\controller\FilesController.java ========
package com.codestats.business.assistance.directory.controller;

import com.codestats.business.assistance.directory.service.DirectoryService;
import com.framework.spring.annotation.Autowired;
import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;
import com.framework.spring.mvc.RequestContextHolder;
import com.framework.tomcat.connector.Request;
import com.framework.util.JsonUtil;

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

    // 获取根类型参数
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
        // 简化实现：实际应处理 multipart，此处示例使用 Base64 接收文件内容（前端需配合）
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

    // ---------- 辅助方法 ----------
    private Map<String, String> parseQueryParams() {
        Request request = RequestContextHolder.getRequest();
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
        Request request = RequestContextHolder.getRequest();
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