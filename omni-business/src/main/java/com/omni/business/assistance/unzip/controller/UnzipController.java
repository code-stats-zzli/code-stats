package com.omni.business.assistance.unzip.controller;

import com.omni.business.assistance.unzip.service.UnzipService;
import com.omni.business.assistance.unzip.service.UnzipTaskManager;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.BizExceptions;
import com.omni.business.common.util.ResponseHelper;
import com.omni.common.util.RandomUtils;
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
import com.omni.framework.util.ParamUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** ★ L1-01：BaseController；★ L2-01：ResponseHelper；★ A 类 UUID */
@Controller
@RequestMapping("/unzip/api")
public class UnzipController extends BaseController {

    @Autowired
    private UnzipService service;

    @RequestMapping("/supported")
    @ResponseBody
    public String supported() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("types", service.listSupportedTypes());
        return okFlat(r);
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

            Path workRoot = Paths.get(System.getProperty("java.io.tmpdir"), "omni-unzip", RandomUtils.uuidNoDash());
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
            resp.put("taskId", taskId);
            resp.put("originalFileName", fileName);
            resp.put("archiveType", result.getType() != null ? result.getType().name() : null);
            resp.put("totalFiles", result.getTotalFiles());
            resp.put("totalDirs", result.getTotalDirs());
            resp.put("binaryFiles", result.getBinaryFiles());
            resp.put("totalBytes", result.getTotalBytes());
            resp.put("durationMs", result.getDurationMs());
            resp.put("entries", result.getEntries());
            return okFlat(resp);
        } catch (Exception e) {
            return error("解压失败: " + BizExceptions.shortMessage(e));
        }
    }

    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) return error("缺少 taskId");
        Map<String, Object> s = UnzipTaskManager.getStatus(taskId);
        if (s == null) return error("任务不存在或已过期");
        return okFlat(s);
    }

    @RequestMapping("/download-merged")
    public void downloadMerged() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        UnzipTaskManager.TaskContext ctx = UnzipTaskManager.get(taskId);
        if (ctx == null || ctx.outputDir == null) {
            ResponseHelper.writeError(resp, 404, "任务不存在或已过期");
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
            ResponseHelper.writeTextAttachment(resp, merged, fileName);
        } catch (Exception e) {
            ResponseHelper.writeError(resp, 500, "生成合并文件失败: " + e.getMessage());
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
            ResponseHelper.writeError(resp, 404, "任务不存在或已过期");
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
            ResponseHelper.writeZipAttachment(resp, zip, fileName);
        } catch (Exception e) {
            ResponseHelper.writeError(resp, 500, "打包失败: " + e.getMessage());
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
            return okFlat(data);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private static String stripExt(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();
        if (lower.endsWith(".tar.gz")) return name.substring(0, name.length() - 7);
        if (lower.endsWith(".tgz")) return name.substring(0, name.length() - 4);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}