package com.omni.business.assistance.directory.controller;

import com.omni.business.assistance.directory.service.DirectoryService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/files/api")
public class FilesController {

    @Autowired
    private DirectoryService directoryService;

    // ==================== 本地目录管理 ====================

    private String getRootType(Map<String, String> params) {
        String rt = params.get("rootType");
        if (rt == null || rt.isEmpty()) {
            rt = DirectoryService.ROOT_TYPE_LOCAL;
        }
        // 兼容旧参数：server -> resources（走资源管理专用接口，本地接口保持 local）
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

    @RequestMapping("/download")
    public void downloadFile() throws Exception {
        HttpServletResponse response = getResponse();
        if (response == null) throw new RuntimeException("No response available");
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

    @RequestMapping("/downloadZip")
    public void downloadDirectoryAsZip() throws Exception {
        HttpServletResponse response = getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null || path.trim().isEmpty()) {
            response.setStatus(400, "Bad Request");
            response.write("Missing parameter: path");
            response.send();
            return;
        }
        String zipName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (zipName.isEmpty()) zipName = "root";
        zipName += ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");
        response.setContentType("application/zip");
        response.setStatus(200, "OK");
        try {
            directoryService.zipDirectory(rootType, path, response.getRawOutputStream());
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            e.printStackTrace();
            try { response.getRawOutputStream().close(); } catch (IOException ignored) {}
        }
    }

    // ==================== 资源管理 API ====================

    /** 资源类型列表（含中文名） */
    @RequestMapping("/resources/types")
    @ResponseBody
    public String listResourceTypes() {
        try {
            return JsonUtil.toJson(directoryService.getResourceTypes());
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /** 各类型资源数量统计 */
    @RequestMapping("/resources/count")
    @ResponseBody
    public String countResources() {
        try {
            return JsonUtil.toJson(directoryService.countResources());
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /** 列出某类型的资源（显示名已剥离 uuid） */
    @RequestMapping("/resources/list")
    @ResponseBody
    public String listResources() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        if (type == null || type.isEmpty()) return errorResponse("缺少 type 参数");
        try {
            return JsonUtil.toJson(directoryService.listResources(type));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /** 上传资源（multipart） */
    @RequestMapping("/resources/upload")
    @ResponseBody
    public String uploadResource() {
        HttpServletRequest req = RequestContextHolder.getRequest();
        if (req == null) return errorResponse("无法获取请求对象");
        MultipartResolver multipart = req.getMultipartResolver();
        if (multipart == null) return errorResponse("不支持 multipart 请求");

        FileItem fileItem = multipart.getFileItem("file");
        String type = multipart.getParameter("type");
        if (fileItem == null) return errorResponse("缺少 file 字段");
        if (type == null || type.isEmpty()) return errorResponse("缺少 type 字段");

        try {
            String originalName = fileItem.getFileName();
            if (originalName == null || originalName.isEmpty()) {
                return errorResponse("文件名为空");
            }
            byte[] content = fileItem.getBytes();
            Map<String, Object> result = directoryService.uploadResource(type, originalName, content);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", result);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return errorResponse("上传失败: " + e.getMessage());
        }
    }

    /** 删除资源 */
    @RequestMapping("/resources/delete")
    @ResponseBody
    public String deleteResource() {
        try {
            String body = getRequestBody();
            Map<String, Object> req = parseJson(body);
            String type = (String) req.get("type");
            String displayName = (String) req.get("displayName");
            if (type == null || displayName == null) return errorResponse("缺少 type 或 displayName");
            directoryService.deleteResource(type, displayName);
            return successResponse();
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /** 下载资源（按显示名） */
    @RequestMapping("/resources/download")
    public void downloadResource() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String name = params.get("name");
        if (type == null || name == null) {
            response.setStatus(400, "Bad Request");
            response.write("Missing parameters: type, name");
            response.send();
            return;
        }
        try {
            Path filePath = directoryService.getResourcePath(type, name);
            if (!Files.exists(filePath)) {
                response.setStatus(404, "Not Found");
                response.write("Resource not found");
                response.send();
                return;
            }
            String encodedFileName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + encodedFileName);
            response.setContentType("application/octet-stream");
            byte[] data = Files.readAllBytes(filePath);
            response.getRawOutputStream().write(data);
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(500, "Internal Server Error");
            response.write("下载失败: " + e.getMessage());
            response.send();
        }
    }

    /** 预览资源（inline，用于图片缩略图等） */
    @RequestMapping("/resources/preview")
    public void previewResource() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String name = params.get("name");
        if (type == null || name == null) {
            response.setStatus(400, "Bad Request");
            response.send();
            return;
        }
        try {
            Path filePath = directoryService.getResourcePath(type, name);
            if (!Files.exists(filePath)) {
                response.setStatus(404, "Not Found");
                response.send();
                return;
            }
            response.setContentType(detectContentType(filePath.getFileName().toString()));
            response.setHeader("Content-Disposition", "inline");
            byte[] data = Files.readAllBytes(filePath);
            response.getRawOutputStream().write(data);
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(500, "Internal Server Error");
            response.send();
        }
    }

    private String detectContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png"))              return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif"))              return "image/gif";
        if (lower.endsWith(".svg"))              return "image/svg+xml";
        if (lower.endsWith(".webp"))             return "image/webp";
        if (lower.endsWith(".bmp"))              return "image/bmp";
        if (lower.endsWith(".ico"))              return "image/x-icon";
        if (lower.endsWith(".pdf"))              return "application/pdf";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css"))              return "text/css; charset=utf-8";
        if (lower.endsWith(".js"))               return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json"))             return "application/json; charset=utf-8";
        if (lower.endsWith(".txt"))              return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    // ==================== 辅助方法 ====================

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