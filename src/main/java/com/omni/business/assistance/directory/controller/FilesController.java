package com.omni.business.assistance.directory.controller;

import com.omni.business.assistance.directory.service.DirectoryService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
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

    private static final Logger log = LoggerFactory.getLogger(FilesController.class);

    @Autowired
    private DirectoryService directoryService;

    // ==================== 本地目录（保持不变） ====================

    private String getRootType(Map<String, String> params) {
        String rt = params.get("rootType");
        if (rt == null || rt.isEmpty()) rt = DirectoryService.ROOT_TYPE_LOCAL;
        return rt;
    }

    @RequestMapping("/root")
    @ResponseBody
    public String getRootInfo() throws Exception {
        Map<String, String> params = parseQueryParams();
        return JsonUtil.toJson(directoryService.getRootInfo(getRootType(params)));
    }

    @RequestMapping("/tree")
    @ResponseBody
    public String getTree() throws Exception {
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.getOrDefault("path", "");
        boolean withChildren = !"false".equalsIgnoreCase(params.get("withChildren"));
        return JsonUtil.toJson(directoryService.getTreeNode(rootType, path, withChildren));
    }

    @RequestMapping("/list")
    @ResponseBody
    public String listFiles() throws Exception {
        Map<String, String> params = parseQueryParams();
        String rootType = getRootType(params);
        String path = params.getOrDefault("path", "");
        return JsonUtil.toJson(directoryService.listDirectory(rootType, path));
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
        String fullPath = parentPath.isEmpty() ? fileName : parentPath + "/" + fileName;
        directoryService.writeBytes(rootType, fullPath, bytes);
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
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
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
        String encoded = URLEncoder.encode(zipName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
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

    @RequestMapping("/resources/types")
    @ResponseBody
    public String listResourceTypes() {
        try { return JsonUtil.toJson(directoryService.getResourceTypes()); }
        catch (Exception e) { return errorResponse(e.getMessage()); }
    }

    @RequestMapping("/resources/count")
    @ResponseBody
    public String countResources() {
        try { return JsonUtil.toJson(directoryService.countResources()); }
        catch (Exception e) { return errorResponse(e.getMessage()); }
    }

    /** 列出某类型某路径下的资源（目录 + 文件） */
    @RequestMapping("/resources/list")
    @ResponseBody
    public String listResources() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.getOrDefault("path", "");
        if (type == null || type.isEmpty()) return errorResponse("缺少 type 参数");
        try {
            return JsonUtil.toJson(directoryService.listResources(type, path));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /** 新增子目录 */
    @RequestMapping("/resources/mkdir")
    @ResponseBody
    public String createResourceSubDir() {
        try {
            String body = getRequestBody();
            Map<String, Object> req = parseJson(body);
            String type = (String) req.get("type");
            String path = (String) req.getOrDefault("path", "");
            String name = (String) req.get("name");
            if (type == null || name == null) return errorResponse("缺少 type 或 name");
            Map<String, Object> result = directoryService.createSubDirectory(type, path, name);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", result);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /**
     * 上传资源（multipart）。
     *
     * <p>普通类型（images/pages/sql/attachments/others）：
     *   只需 file + type + path，后端按 {basename}_{uuid}.ext 命名。</p>
     *
     * <p>驱动类型（drivers）：
     *   额外需要 dbType（mysql/oracle/sqlserver/postgresql）+ version，
     *   后端按 {dbType}_{version}_{原始文件名}.jar 命名，
     *   严格限制只允许 .jar；同 dbType + version 不可重复。</p>
     */
    @RequestMapping("/resources/upload")
    @ResponseBody
    public String uploadResource() {
        HttpServletRequest req = RequestContextHolder.getRequest();
        if (req == null) return errorResponse("无法获取请求对象");

        MultipartResolver multipart = req.getMultipartResolver();
        if (multipart == null) return errorResponse("不支持 multipart 请求");

        FileItem fileItem = multipart.getFileItem("file");
        String type = multipart.getParameter("type");
        String path = multipart.getParameter("path");
        // ★ 驱动专用参数（普通类型忽略）
        String dbType = multipart.getParameter("dbType");
        String version = multipart.getParameter("version");
        if (path == null) path = "";

        if (fileItem == null) return errorResponse("缺少 file 字段");
        if (type == null || type.isEmpty()) return errorResponse("缺少 type 字段");

        try {
            String originalName = fileItem.getFileName();
            if (originalName == null || originalName.isEmpty()) return errorResponse("文件名为空");

            // ★ 驱动类型：Controller 层做前置校验，Service 层还会再校验一次
            if (DirectoryService.RES_TYPE_DRIVERS.equals(type)) {
                if (!originalName.toLowerCase().endsWith(".jar")) {
                    return errorResponse("驱动只允许上传 .jar 文件");
                }
                if (dbType == null || dbType.trim().isEmpty()) {
                    return errorResponse("驱动上传必须提供 dbType（mysql/oracle/sqlserver/postgresql）");
                }
                if (version == null || version.trim().isEmpty()) {
                    return errorResponse("驱动上传必须提供 version");
                }
            }

            byte[] content = fileItem.getBytes();
            Map<String, Object> result = directoryService.uploadResource(
                    type, path, originalName, content, dbType, version);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", result);
            return JsonUtil.toJson(resp);
        } catch (IllegalArgumentException | SecurityException e) {
            // 参数 / 路径校验类错误，直接展示给用户
            return errorResponse(e.getMessage());
        } catch (IOException e) {
            // 文件写入类错误（如驱动重复）
            log.warn("上传资源失败(IO): type={}, path={}, msg={}", type, path, e.getMessage());
            return errorResponse("上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("上传资源失败: type={}, path={}", type, path, e);
            return errorResponse("上传失败: " + e.getMessage());
        }
    }

    /** 删除资源（文件或目录，递归） */
    @RequestMapping("/resources/delete")
    @ResponseBody
    public String deleteResource() {
        try {
            String body = getRequestBody();
            Map<String, Object> req = parseJson(body);
            String type = (String) req.get("type");
            String path = (String) req.get("path");
            if (type == null || path == null) return errorResponse("缺少 type 或 path");
            directoryService.deleteResource(type, path);
            return successResponse();
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /** 下载资源 */
    @RequestMapping("/resources/download")
    public void downloadResource() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.get("path");
        if (type == null || path == null) {
            response.setStatus(400, "Bad Request");
            response.write("Missing parameters: type, path");
            response.send();
            return;
        }
        try {
            Path filePath = directoryService.getResourceFilePath(type, path);
            if (!Files.exists(filePath)) {
                response.setStatus(404, "Not Found");
                response.send();
                return;
            }
            String fileName = filePath.getFileName().toString();
            // ★ 驱动类型的展示名要反解（去掉 {dbType}_{version}_ 前缀）
            String displayName = directoryService.parseDisplayName(type, fileName);
            String encoded = URLEncoder.encode(displayName, StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
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

    /** 预览资源（inline，用于图片/HTML） */
    @RequestMapping("/resources/preview")
    public void previewResource() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.get("path");
        if (type == null || path == null) {
            response.setStatus(400, "Bad Request");
            response.send();
            return;
        }
        try {
            Path filePath = directoryService.getResourceFilePath(type, path);
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

    /** 读取文本内容（SQL / 页面等） */
    @RequestMapping("/resources/read")
    @ResponseBody
    public String readResourceContent() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.get("path");
        if (type == null || path == null) return errorResponse("缺少 type 或 path");
        try {
            String content = directoryService.readResourceContent(type, path);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("content", content);
            resp.put("type", type);
            resp.put("path", path);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    // ==================== 辅助 ====================

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
        if (lower.endsWith(".txt") || lower.endsWith(".sql")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

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

    private HttpServletResponse getResponse() { return ResponseContextHolder.getResponse(); }

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