package com.omni.business.assistance.compress.controller;

import com.omni.business.assistance.compress.service.CompressService;
import com.omni.business.assistance.compress.service.CompressTaskManager;
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
import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.CompressEntry;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Controller
@RequestMapping("/compress/api")
public class CompressController {

    @Autowired
    private CompressService service;

    @RequestMapping("/supported")
    @ResponseBody
    public String supported() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("types", service.listSupportedTypes());
        return JsonUtil.toJson(r);
    }

    /** 上传多个文件 → 压缩 + 生成 merge-file，返回 taskId */
    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");
            MultipartResolver mp = req.getMultipartResolver();
            if (mp == null) return error("请求不是 multipart");

            String targetExt = mp.getParameter("target");
            ArchiveType targetType = ArchiveType.ofExt(targetExt);
            if (targetType == null) return error("无效的目标格式: " + targetExt);

            String encoding = mp.getParameter("encoding");
            if (encoding == null || encoding.isEmpty()) encoding = "UTF-8";

            Set<String> fileNames = mp.getFileNames();
            if (fileNames == null || fileNames.isEmpty()) return error("没有上传文件");

            List<CompressEntry> entries = new ArrayList<>();
            for (String field : fileNames) {
                FileItem item = mp.getFileItem(field);
                if (item == null) continue;
                String fileName = item.getFileName();
                if (fileName == null || fileName.isEmpty()) continue;
                String path = fileName.replace('\\', '/');
                while (path.startsWith("/")) path = path.substring(1);
                CompressEntry e = new CompressEntry();
                e.setPath(path);
                e.setContent(item.getBytes());
                e.setDirectory(false);
                entries.add(e);
            }
            if (entries.isEmpty()) return error("没有有效的文件");

            String archiveFileName = "archive_" + System.currentTimeMillis() + "." + targetType.ext;
            CompressService.CompressOutcome outcome =
                    service.compress(entries, targetType, archiveFileName, encoding);

            String taskId = CompressTaskManager.registerCompleted(
                    outcome.result, outcome.archiveFile, outcome.mergedFile, archiveFileName);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("taskId", taskId);
            resp.put("archiveFileName", archiveFileName);
            resp.put("archiveType", targetType.name());
            resp.put("fileCount", outcome.result.getFileCount());
            resp.put("rawBytes", outcome.result.getRawBytes());
            resp.put("archiveBytes", outcome.result.getArchiveBytes());
            resp.put("durationMs", outcome.result.getDurationMs());
            if (outcome.mergeStat != null) {
                resp.put("mergedFileCount", outcome.mergeStat.totalFiles);
                resp.put("mergedTextFiles", outcome.mergeStat.textFiles);
                resp.put("mergedBinaryFiles", outcome.mergeStat.binaryFiles);
            }
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return error("压缩失败: " + rootCauseMessage(e));
        }
    }

    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) return error("缺少 taskId");
        Map<String, Object> s = CompressTaskManager.getStatus(taskId);
        if (s == null) return error("任务不存在或已过期");
        s.put("success", true);
        return JsonUtil.toJson(s);
    }

    @RequestMapping("/download-archive")
    public void downloadArchive() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        CompressTaskManager.TaskContext ctx = CompressTaskManager.get(taskId);
        if (ctx == null || ctx.archiveFile == null || !Files.exists(ctx.archiveFile)) {
            writeError(resp, 404, "Not Found", "任务不存在或文件已过期");
            return;
        }
        try (InputStream in = Files.newInputStream(ctx.archiveFile)) {
            String encoded = URLEncoder.encode(ctx.archiveFileName, StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + ctx.archiveFileName + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType("application/octet-stream");
            resp.sendStatic(in, "application/octet-stream", Files.size(ctx.archiveFile));
        }
    }

    @RequestMapping("/download-merged")
    public void downloadMerged() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        CompressTaskManager.TaskContext ctx = CompressTaskManager.get(taskId);
        if (ctx == null || ctx.mergedFile == null || !Files.exists(ctx.mergedFile)) {
            writeError(resp, 404, "Not Found", "任务不存在或合并文件已过期");
            return;
        }
        try (InputStream in = Files.newInputStream(ctx.mergedFile)) {
            String fileName = baseName(ctx.archiveFileName) + "_merged.txt";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                    .replaceAll("\\+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType("text/plain; charset=utf-8");
            resp.sendStatic(in, "text/plain; charset=utf-8", Files.size(ctx.mergedFile));
        }
    }

    private static String baseName(String fileName) {
        if (fileName == null) return "archive";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".tar.gz")) return fileName.substring(0, fileName.length() - 7);
        if (lower.endsWith(".tgz")) return fileName.substring(0, fileName.length() - 4);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }

    private void writeError(HttpServletResponse resp, int code, String reason, String msg) throws Exception {
        resp.setStatus(code, reason);
        resp.setContentType("application/json; charset=utf-8");
        resp.write(JsonUtil.toJson(Map.of("success", false, "error", msg)));
        resp.send();
    }

    private static String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}