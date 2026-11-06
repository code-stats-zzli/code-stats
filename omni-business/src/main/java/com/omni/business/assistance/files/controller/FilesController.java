package com.omni.business.assistance.files.controller;

import com.omni.business.assistance.directory.service.DirectoryService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.ResponseHelper;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ★ L1-01 BaseController；★ L1-03 ParamUtils；★ L1-04 JsonUtil；
 * ★ L2-01 ResponseHelper。
 */
@Controller
@RequestMapping("/files/api")
public class FilesController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(FilesController.class);

    @Autowired
    private DirectoryService directoryService;

    private String getRootType(Map<String, String> params) {
        String rt = params.get("rootType");
        if (rt == null || rt.isEmpty()) rt = DirectoryService.ROOT_TYPE_LOCAL;
        return rt;
    }

    @RequestMapping("/root")
    @ResponseBody
    public String getRootInfo() throws Exception {
        Map<String, String> params = ParamUtils.parseQueryParams();
        return JsonUtil.toJson(directoryService.getRootInfo(getRootType(params)));
    }

    @RequestMapping("/tree")
    @ResponseBody
    public String getTree() throws Exception {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String rootType = getRootType(params);
        String path = params.getOrDefault("path", "");
        boolean withChildren = !"false".equalsIgnoreCase(params.get("withChildren"));
        return JsonUtil.toJson(directoryService.getTreeNode(rootType, path, withChildren));
    }

    @RequestMapping("/list")
    @ResponseBody
    public String listFiles() throws Exception {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String rootType = getRootType(params);
        String path = params.getOrDefault("path", "");
        return JsonUtil.toJson(directoryService.listDirectory(rootType, path));
    }

    @RequestMapping("/read")
    @ResponseBody
    public String readFile() throws Exception {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null) return error("缺少path参数");
        String content = directoryService.readFile(rootType, path);
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("path", path);
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/save")
    @ResponseBody
    public String saveFile() throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String path = (String) req.get("path");
        String content = (String) req.getOrDefault("content", "");
        if (path == null) return error("缺少path参数");
        directoryService.writeFile(rootType, path, content);
        return ok();
    }

    @RequestMapping("/mkdir")
    @ResponseBody
    public String mkdir() throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String parentPath = (String) req.getOrDefault("parentPath", "");
        String name = (String) req.get("name");
        if (name == null || name.trim().isEmpty()) return error("目录名不能为空");
        directoryService.createDirectory(rootType, parentPath, name);
        return ok();
    }

    @RequestMapping("/createFile")
    @ResponseBody
    public String createFile() throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String parentPath = (String) req.getOrDefault("parentPath", "");
        String fileName = (String) req.get("fileName");
        if (fileName == null || fileName.trim().isEmpty()) return error("文件名不能为空");
        directoryService.createFile(rootType, parentPath, fileName);
        return ok();
    }

    @RequestMapping("/rename")
    @ResponseBody
    public String rename() throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String path = (String) req.get("path");
        String newName = (String) req.get("newName");
        if (path == null || newName == null) return error("缺少参数");
        directoryService.rename(rootType, path, newName);
        return ok();
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete() throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String path = (String) req.get("path");
        if (path == null) return error("缺少path参数");
        directoryService.deletePath(rootType, path);
        return ok();
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
        String rootType = (String) req.getOrDefault("rootType", DirectoryService.ROOT_TYPE_LOCAL);
        String parentPath = (String) req.getOrDefault("parentPath", "");
        String fileName = (String) req.get("fileName");
        String base64Content = (String) req.get("content");
        if (fileName == null || base64Content == null) return error("缺少参数");
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Content);
        String fullPath = parentPath.isEmpty() ? fileName : parentPath + "/" + fileName;
        directoryService.writeBytes(rootType, fullPath, bytes);
        return ok();
    }

    @RequestMapping("/download")
    public void downloadFile() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null || path.trim().isEmpty()) {
            ResponseHelper.writeError(response, 400, "缺少 path");
            return;
        }
        try {
            byte[] data = directoryService.readFileBytes(rootType, path);
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            ResponseHelper.writeAttachment(response, data, fileName, "application/octet-stream");
        } catch (Exception e) {
            ResponseHelper.writeError(response, 500, "下载失败: " + e.getMessage());
        }
    }

    @RequestMapping("/downloadZip")
    public void downloadDirectoryAsZip() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String rootType = getRootType(params);
        String path = params.get("path");
        if (path == null || path.trim().isEmpty()) {
            ResponseHelper.writeError(response, 400, "缺少 path");
            return;
        }
        String zipName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (zipName.isEmpty()) zipName = "root";
        zipName += ".zip";
        try {
            // zip 目录走流式输出，先设置 header 再写流
            String encoded = java.net.URLEncoder.encode(zipName, java.nio.charset.StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            response.setContentType("application/zip");
            response.setStatus(200, "OK");
            directoryService.zipDirectory(rootType, path, response.getRawOutputStream());
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            log.error("打包下载失败", e);
            try { response.getRawOutputStream().close(); } catch (IOException ignored) {}
        }
    }

    // ==================== 资源管理 API ====================

    @RequestMapping("/resources/types")
    @ResponseBody
    public String listResourceTypes() {
        try { return JsonUtil.toJson(directoryService.getResourceTypes()); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/resources/count")
    @ResponseBody
    public String countResources() {
        try { return JsonUtil.toJson(directoryService.countResources()); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/resources/list")
    @ResponseBody
    public String listResources() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.getOrDefault("path", "");
        if (type == null || type.isEmpty()) return error("缺少 type 参数");
        try { return JsonUtil.toJson(directoryService.listResources(type, path)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/resources/mkdir")
    @ResponseBody
    public String createResourceSubDir() {
        try {
            Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
            String type = (String) req.get("type");
            String path = (String) req.getOrDefault("path", "");
            String name = (String) req.get("name");
            if (type == null || name == null) return error("缺少 type 或 name");
            Map<String, Object> result = directoryService.createSubDirectory(type, path, name);
            Map<String, Object> resp = new HashMap<>();
            resp.put("data", result);
            return okFlat(resp);
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/resources/upload")
    @ResponseBody
    public String uploadResource() {
        HttpServletRequest req = RequestContextHolder.getRequest();
        if (req == null) return error("无法获取请求对象");
        MultipartResolver multipart = req.getMultipartResolver();
        if (multipart == null) return error("不支持 multipart 请求");

        FileItem fileItem = multipart.getFileItem("file");
        String type = multipart.getParameter("type");
        String path = multipart.getParameter("path");
        String dbType = multipart.getParameter("dbType");
        String version = multipart.getParameter("version");
        if (path == null) path = "";

        if (fileItem == null) return error("缺少 file 字段");
        if (type == null || type.isEmpty()) return error("缺少 type 字段");

        try {
            String originalName = fileItem.getFileName();
            if (originalName == null || originalName.isEmpty()) return error("文件名为空");

            if (DirectoryService.RES_TYPE_DRIVERS.equals(type)) {
                if (!originalName.toLowerCase().endsWith(".jar")) return error("驱动只允许上传 .jar 文件");
                if (dbType == null || dbType.trim().isEmpty()) return error("驱动上传必须提供 dbType");
                if (version == null || version.trim().isEmpty()) return error("驱动上传必须提供 version");
            }
            if (DirectoryService.RES_TYPE_DEPLOY.equals(type)) {
                if (!originalName.toLowerCase().endsWith(".jar")) return error("项目部署目录只允许上传 .jar 文件");
            }
            byte[] content = fileItem.getBytes();
            Map<String, Object> result = directoryService.uploadResource(
                    type, path, originalName, content, dbType, version);
            Map<String, Object> resp = new HashMap<>();
            resp.put("data", result);
            return okFlat(resp);
        } catch (IllegalArgumentException | SecurityException e) {
            return error(e.getMessage());
        } catch (IOException e) {
            log.warn("上传资源失败(IO): type={}, path={}, msg={}", type, path, e.getMessage());
            return error("上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("上传资源失败: type={}, path={}", type, path, e);
            return error("上传失败: " + e.getMessage());
        }
    }

    @RequestMapping("/resources/delete")
    @ResponseBody
    public String deleteResource() {
        try {
            Map<String, Object> req = JsonUtil.parseJson(getRequestBody());
            String type = (String) req.get("type");
            String path = (String) req.get("path");
            if (type == null || path == null) return error("缺少 type 或 path");
            directoryService.deleteResource(type, path);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/resources/download")
    public void downloadResource() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.get("path");
        if (type == null || path == null) {
            ResponseHelper.writeError(response, 400, "缺少 type 或 path");
            return;
        }
        try {
            Path filePath = directoryService.getResourceFilePath(type, path);
            if (!Files.exists(filePath)) { ResponseHelper.writeError(response, 404, "文件不存在"); return; }
            String fileName = filePath.getFileName().toString();
            String displayName = directoryService.parseDisplayName(type, fileName);
            ResponseHelper.writeAttachment(response, filePath, displayName);
        } catch (Exception e) {
            ResponseHelper.writeError(response, 500, "下载失败: " + e.getMessage());
        }
    }

    @RequestMapping("/resources/preview")
    public void previewResource() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.get("path");
        if (type == null || path == null) {
            ResponseHelper.writeError(response, 400, "缺少 type 或 path");
            return;
        }
        try {
            Path filePath = directoryService.getResourceFilePath(type, path);
            if (!Files.exists(filePath)) { ResponseHelper.writeError(response, 404, "文件不存在"); return; }
            response.setContentType(detectContentType(filePath.getFileName().toString()));
            response.setHeader("Content-Disposition", "inline");
            byte[] data = Files.readAllBytes(filePath);
            response.getRawOutputStream().write(data);
            response.getRawOutputStream().flush();
        } catch (Exception e) {
            ResponseHelper.writeError(response, 500, "预览失败: " + e.getMessage());
        }
    }

    @RequestMapping("/resources/read")
    @ResponseBody
    public String readResourceContent() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String type = params.get("type");
        String path = params.get("path");
        if (type == null || path == null) return error("缺少 type 或 path");
        try {
            String content = directoryService.readResourceContent(type, path);
            Map<String, Object> resp = new HashMap<>();
            resp.put("content", content);
            resp.put("type", type);
            resp.put("path", path);
            return okFlat(resp);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 辅助 ====================

    private String detectContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".txt") || lower.endsWith(".sql")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private String getRequestBody() throws IOException {
        HttpServletRequest request = RequestContextHolder.getRequest();
        return request.getBody();
    }
}