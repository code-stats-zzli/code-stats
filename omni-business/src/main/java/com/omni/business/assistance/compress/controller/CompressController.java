package com.omni.business.assistance.compress.controller;

import com.omni.business.assistance.compress.service.CompressService;
import com.omni.business.assistance.compress.service.CompressTaskManager;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.BizExceptions;
import com.omni.business.common.util.BizResults;
import com.omni.business.common.util.ResponseHelper;
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
import com.omni.framework.util.ParamUtils;

import java.nio.file.Files;
import java.util.*;

/** ★ L1-01：BaseController；★ L2-01：ResponseHelper */
@Controller
@RequestMapping("/compress/api")
public class CompressController extends BaseController {

    @Autowired
    private CompressService service;

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
            CompressService.CompressOutcome outcome = service.compress(entries, targetType, archiveFileName, encoding);
            String taskId = CompressTaskManager.registerCompleted(
                    outcome.result, outcome.archiveFile, outcome.mergedFile, archiveFileName);

            Map<String, Object> resp = new LinkedHashMap<>();
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
            return okFlat(resp);
        } catch (Exception e) {
            return error("压缩失败: " + BizExceptions.shortMessage(e));
        }
    }

    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) return error("缺少 taskId");
        Map<String, Object> s = CompressTaskManager.getStatus(taskId);
        if (s == null) return error("任务不存在或已过期");
        return okFlat(s);
    }

    @RequestMapping("/download-archive")
    public void downloadArchive() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        CompressTaskManager.TaskContext ctx = CompressTaskManager.get(taskId);
        if (ctx == null || ctx.archiveFile == null || !Files.exists(ctx.archiveFile)) {
            ResponseHelper.writeError(resp, 404, "任务不存在或文件已过期");
            return;
        }
        ResponseHelper.writeAttachment(resp, ctx.archiveFile, ctx.archiveFileName);
    }

    @RequestMapping("/download-merged")
    public void downloadMerged() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String taskId = params.get("taskId");
        CompressTaskManager.TaskContext ctx = CompressTaskManager.get(taskId);
        if (ctx == null || ctx.mergedFile == null || !Files.exists(ctx.mergedFile)) {
            ResponseHelper.writeError(resp, 404, "任务不存在或合并文件已过期");
            return;
        }
        String fileName = baseName(ctx.archiveFileName) + "_merged.txt";
        ResponseHelper.writeTextAttachment(resp, ctx.mergedFile, fileName);
    }

    private static String baseName(String fileName) {
        if (fileName == null) return "archive";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".tar.gz")) return fileName.substring(0, fileName.length() - 7);
        if (lower.endsWith(".tgz")) return fileName.substring(0, fileName.length() - 4);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}