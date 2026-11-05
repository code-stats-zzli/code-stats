package com.omni.business.assistance.unzip.controller;

import com.omni.business.assistance.unzip.service.UnzipService;
import com.omni.business.assistance.unzip.service.UnzipTaskManager;
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
import com.omni.framework.unzip.ExtractRequest;
import com.omni.framework.unzip.ExtractResult;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Controller
@RequestMapping("/unzip/api")
public class UnzipController {

    @Autowired
    private UnzipService service;

    @RequestMapping("/supported")
    @ResponseBody
    public String supported() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("types", service.listSupportedTypes());
        return JsonUtil.toJson(r);
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");
            MultipartResolver mp = req.getMultipartResolver();
            if (mp == null) return error("请求不是 multipart");

            FileItem fileItem = mp.getFileItem("file");
            if (fileItem == null) return error("缺少 file 字段");

            String fileName = fileItem.getFileName();
            if (fileName == null || fileName.isEmpty()) return error("文件名为空");

            String encoding = mp.getParameter("encoding");
            if (encoding == null || encoding.trim().isEmpty()) encoding = "UTF-8";

            byte[] bytes = fileItem.getBytes();
            if (bytes == null || bytes.length == 0) return error("文件内容为空");

            Path workRoot = Paths.get(System.getProperty("java.io.tmpdir"), "omni-unzip",
                    UUID.randomUUID().toString().replace("-", ""));
            Path outputDir = workRoot.resolve("files");
            Files.createDirectories(outputDir);

            ExtractRequest extractReq = new ExtractRequest();
            extractReq.setSourceBytes(bytes);
            extractReq.setSourceFileName(fileName);
            extractReq.setTargetDir(outputDir);
            extractReq.setEncoding(encoding);

            ExtractResult result = service.extract(extractReq);
            String taskId = UnzipTaskManager.registerCompleted(result, fileName);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("taskId", taskId);
            resp.put("originalFileName", fileName);
            resp.put("archiveType", result.getType() != null ? result.getType().name() : null);
            resp.put("totalFiles", result.getTotalFiles());
            resp.put("totalDirs", result.getTotalDirs());
            resp.put("binaryFiles", result.getBinaryFiles());
            resp.put("totalBytes", result.getTotalBytes());
            resp.put("durationMs", result.getDurationMs());
            resp.put("entries", result.getEntries());
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return error("解压失败: " + rootCauseMessage(e));
        }
    }

    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) return error("缺少 taskId");
        Map<String, Object> s = UnzipTaskManager.getStatus(taskId);
        if (s == null) return error("任务不存在或已过期");
        s.put("success", true);
        return JsonUtil.toJson(s);
    }

    @RequestMapping("/download-merged")
    public void downloadMerged() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        UnzipTaskManager.TaskContext ctx = UnzipTaskManager.get(taskId);
        if (ctx == null || ctx.outputDir == null) {
            writeError(resp, 404, "Not Found", "任务不存在或已过期");
            return;
        }
        try {
            Path merged = ctx.mergedFile;
            if (merged == null || !Files.exists(merged)) {
                Path workRoot = ctx.outputDir.getParent();
                merged = workRoot.resolve("merged.txt");
                service.writeMergedFile(ctx.outputDir, merged);
                ctx.mergedFile = merged;
            }
            String baseName = stripExt(ctx.originalFileName);
            String fileName = (baseName.isEmpty() ? "unzipped" : baseName) + "_merged.txt";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType("text/plain; charset=utf-8");
            try (InputStream in = Files.newInputStream(merged)) {
                resp.sendStatic(in, "text/plain; charset=utf-8", Files.size(merged));
            }
        } catch (Exception e) {
            writeError(resp, 500, "Internal Server Error", "生成合并文件失败: " + e.getMessage());
        }
    }

    @RequestMapping("/download-zip")
    public void downloadZip() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        UnzipTaskManager.TaskContext ctx = UnzipTaskManager.get(taskId);
        if (ctx == null || ctx.outputDir == null) {
            writeError(resp, 404, "Not Found", "任务不存在或已过期");
            return;
        }
        try {
            Path zip = ctx.zipFile;
            if (zip == null || !Files.exists(zip)) {
                Path workRoot = ctx.outputDir.getParent();
                zip = workRoot.resolve("archive.zip");
                service.zipDirectoryToFile(ctx.outputDir, zip);
                ctx.zipFile = zip;
            }
            String baseName = stripExt(ctx.originalFileName);
            String fileName = (baseName.isEmpty() ? "unzipped" : baseName) + ".zip";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType("application/zip");
            try (InputStream in = Files.newInputStream(zip)) {
                resp.sendStatic(in, "application/zip", Files.size(zip));
            }
        } catch (Exception e) {
            writeError(resp, 500, "Internal Server Error", "打包失败: " + e.getMessage());
        }
    }

    @RequestMapping("/preview")
    @ResponseBody
    public String preview() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        String path = params.get("path");
        if (taskId == null || path == null) return error("缺少 taskId 或 path");
        UnzipTaskManager.TaskContext ctx = UnzipTaskManager.get(taskId);
        if (ctx == null || ctx.outputDir == null) return error("任务不存在或已过期");
        try {
            Map<String, Object> data = service.readEntry(ctx.outputDir, path);
            data.put("success", true);
            return JsonUtil.toJson(data);
        } catch (Exception e) {
            return error(e.getMessage());
        }
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
    private static String stripExt(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();
        if (lower.endsWith(".tar.gz")) return name.substring(0, name.length() - 7);
        if (lower.endsWith(".tgz")) return name.substring(0, name.length() - 4);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
    private static String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}