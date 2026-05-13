package com.codestats.business.assistance.directory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;
import com.framework.spring.mvc.RequestContextHolder;
import com.framework.tomcat.connector.Request;
import com.framework.util.JsonUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/files/api")
public class FilesController {

    private static final String LOCAL_ROOT = System.getProperty("user.dir");
    private static final String SERVER_ROOT = "D:/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 获取根路径
    private Path getRootPath(String rootType) {
        if ("server".equals(rootType)) {
            return Paths.get(SERVER_ROOT);
        }
        return Paths.get(LOCAL_ROOT);
    }

    // 安全校验：确保路径在根目录内
    private Path safePath(String rootType, String userPath) throws IOException {
        Path root = getRootPath(rootType);
        Path target;
        if (userPath == null || userPath.trim().isEmpty()) {
            target = root;
        } else {
            target = root.resolve(userPath).normalize();
        }
        if (!target.startsWith(root)) {
            throw new SecurityException("路径越界访问");
        }
        return target;
    }

    /**
     * 获取目录树 (递归到第一层，子节点懒加载)
     * GET /files/api/tree?rootType=local&path=
     */
    @RequestMapping("/tree")
    @ResponseBody
    public String getTree() throws Exception {
        Request request = RequestContextHolder.getRequest();
        Map<String, String> params = parseQuery(request.getQueryString());
        String rootType = params.getOrDefault("rootType", "local");
        String path = params.getOrDefault("path", "");
        Path target = safePath(rootType, path);
        Map<String, Object> node = buildTreeNode(target, rootType, true);
        return JsonUtil.toJson(node);
    }

    private Map<String, Object> buildTreeNode(Path path, String rootType, boolean withChildren) throws IOException {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", path.getFileName().toString());
        node.put("path", path.toString());
        boolean isDir = Files.isDirectory(path);
        node.put("type", isDir ? "directory" : "file");
        if (withChildren && isDir) {
            List<Map<String, Object>> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    // 只展示一层，子节点 children 为空，前端懒加载
                    Map<String, Object> childNode = buildTreeNode(child, rootType, false);
                    children.add(childNode);
                }
            }
            node.put("children", children);
            node.put("expanded", false);
        } else {
            node.put("children", Collections.emptyList());
        }
        return node;
    }

    /**
     * 获取文件列表（当前目录）
     * GET /files/api/list?rootType=local&path=
     */
    @RequestMapping("/list")
    @ResponseBody
    public String listFiles() throws Exception {
        Request request = RequestContextHolder.getRequest();
        Map<String, String> params = parseQuery(request.getQueryString());
        String rootType = params.getOrDefault("rootType", "local");
        String path = params.getOrDefault("path", "");
        Path target = safePath(rootType, path);
        List<Map<String, Object>> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (Path entry : stream) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getFileName().toString());
                item.put("path", entry.toString());
                item.put("type", Files.isDirectory(entry) ? "directory" : "file");
                item.put("size", Files.isRegularFile(entry) ? Files.size(entry) : 0);
                item.put("modified", sdf.format(Files.getLastModifiedTime(entry).toMillis()));
                entries.add(item);
            }
            // 排序：目录在前，文件在后，按名称
            entries.sort((a, b) -> {
                if (a.get("type").equals(b.get("type"))) {
                    return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                }
                return a.get("type").equals("directory") ? -1 : 1;
            });
        }
        return JsonUtil.toJson(entries);
    }

    /**
     * 读取文件内容
     * GET /files/api/read?rootType=local&path=
     */
    @RequestMapping("/read")
    @ResponseBody
    public String readFile() throws Exception {
        Request request = RequestContextHolder.getRequest();
        Map<String, String> params = parseQuery(request.getQueryString());
        String rootType = params.getOrDefault("rootType", "local");
        String path = params.get("path");
        if (path == null) return errorResponse("缺少path参数");
        Path target = safePath(rootType, path);
        if (!Files.isRegularFile(target)) {
            return errorResponse("不是文件");
        }
        String content = new String(Files.readAllBytes(target));
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("path", target.toString());
        return JsonUtil.toJson(result);
    }

    /**
     * 保存文件内容
     * POST /files/api/save
     * Body: { "rootType":"local", "path":"/a/b.txt", "content":"..." }
     */
    @RequestMapping("/save")
    @ResponseBody
    public String saveFile() throws Exception {
        String body = getRequestBody();
        JsonNode root = objectMapper.readTree(body);
        String rootType = root.has("rootType") ? root.get("rootType").asText() : "local";
        String path = root.has("path") ? root.get("path").asText() : null;
        String content = root.has("content") ? root.get("content").asText() : "";
        if (path == null) return errorResponse("缺少path");
        Path target = safePath(rootType, path);
        Files.write(target, content.getBytes());
        return successResponse();
    }

    /**
     * 新建目录
     * POST /files/api/mkdir
     * Body: { "rootType":"local", "parentPath":"/a", "name":"newFolder" }
     */
    @RequestMapping("/mkdir")
    @ResponseBody
    public String mkdir() throws Exception {
        String body = getRequestBody();
        JsonNode root = objectMapper.readTree(body);
        String rootType = root.has("rootType") ? root.get("rootType").asText() : "local";
        String parentPath = root.has("parentPath") ? root.get("parentPath").asText() : "";
        String name = root.has("name") ? root.get("name").asText() : null;
        if (name == null || name.trim().isEmpty()) return errorResponse("目录名不能为空");
        Path parent = safePath(rootType, parentPath);
        Path newDir = parent.resolve(name);
        Files.createDirectories(newDir);
        return successResponse();
    }

    /**
     * 重命名文件或目录
     * POST /files/api/rename
     * Body: { "rootType":"local", "path":"/a/old", "newName":"new" }
     */
    @RequestMapping("/rename")
    @ResponseBody
    public String rename() throws Exception {
        String body = getRequestBody();
        JsonNode root = objectMapper.readTree(body);
        String rootType = root.has("rootType") ? root.get("rootType").asText() : "local";
        String path = root.has("path") ? root.get("path").asText() : null;
        String newName = root.has("newName") ? root.get("newName").asText() : null;
        if (path == null || newName == null) return errorResponse("缺少参数");
        Path source = safePath(rootType, path);
        Path target = source.resolveSibling(newName);
        Files.move(source, target);
        return successResponse();
    }

    /**
     * 删除文件或空目录
     * POST /files/api/delete
     * Body: { "rootType":"local", "path":"/a/file.txt" }
     */
    @RequestMapping("/delete")
    @ResponseBody
    public String delete() throws Exception {
        String body = getRequestBody();
        JsonNode root = objectMapper.readTree(body);
        String rootType = root.has("rootType") ? root.get("rootType").asText() : "local";
        String path = root.has("path") ? root.get("path").asText() : null;
        if (path == null) return errorResponse("缺少path");
        Path target = safePath(rootType, path);
        if (Files.isDirectory(target)) {
            Files.delete(target); // 仅删除空目录
        } else {
            Files.delete(target);
        }
        return successResponse();
    }

    /**
     * 上传文件
     * POST /files/api/upload (multipart/form-data)
     */
    @RequestMapping("/upload")
    @ResponseBody
    public String upload() throws Exception {
        // 简化实现：从 multipart 解析文件（自研框架暂不支持 multipart，这里做简单处理）
        // 实际生产建议使用 Apache Commons FileUpload，此处返回错误提示
        return errorResponse("上传功能暂未完整实现，请使用新建文件方式");
    }

    // 辅助方法
    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return map;
    }

    private String getRequestBody() throws IOException {
        Request request = RequestContextHolder.getRequest();
        return request.getBody();
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