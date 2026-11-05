package com.omni.business.assistance.formatconverter.controller;

import com.omni.business.assistance.formatconverter.service.ConvertTaskManager;
import com.omni.business.assistance.formatconverter.service.FormatConverterService;
import com.omni.business.common.converter.TempFileManager;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.converter.FormatType;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/converter/api")
public class FormatConverterController {

    @Autowired
    private FormatConverterService service;

    /** 支持的转换能力列表 */
    @RequestMapping("/supported")
    @ResponseBody
    public String supported() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("converters", service.getRegistry().listSupported());
        return JsonUtil.toJson(resp);
    }

    /** 文件上传转换 */
    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        MultipartResolver mp = RequestContextHolder.getRequest().getMultipartResolver();
        if (mp == null) return err("请求不是 multipart");
        FileItem fileItem = mp.getFileItem("file");
        if (fileItem == null) return err("缺少 file 字段");
        String targetExt = mp.getParameter("target");
        boolean async = "true".equalsIgnoreCase(mp.getParameter("async"));
        try {
            ConvertRequest req = new ConvertRequest();
            req.setSourceFileName(fileItem.getFileName());
            req.setSourceBytes(fileItem.getBytes());
            req.setTargetType(FormatType.ofExt(targetExt));
            if (req.getTargetType() == null) return err("无效的目标格式: " + targetExt);
            String srcExt = mp.getParameter("sourceType");
            if (srcExt != null && !srcExt.isEmpty()) {
                req.setSourceType(FormatType.ofExt(srcExt));
            }

            if (async) {
                String taskId = ConvertTaskManager.submit(req, service::convert);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("success", true);
                r.put("async", true);
                r.put("taskId", taskId);
                return JsonUtil.toJson(r);
            }

            ConvertResult result = service.convert(req);
            if (!result.isSuccess()) return err(result.getMessage());
            String storedName = TempFileManager.store(result);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("storedName", storedName);
            resp.put("fileName", result.getSuggestedFileName());
            resp.put("targetType", result.getTargetType().ext);
            resp.put("size", result.size());
            resp.put("meta", result.getMeta());
            resp.put("downloadUrl", "/converter/api/download?name="
                    + URLEncoder.encode(storedName, "UTF-8"));
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return err("上传转换失败: " + e.getMessage());
        }
    }

    /** 纯文本转换（JSON ↔ XML 等） */
    @RequestMapping("/text")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String text(@RequestBody Map<String, Object> body) {
        if (body == null) return err("请求体为空");
        try {
            ConvertRequest req = new ConvertRequest();
            req.setSourceText(str(body.get("source")));
            req.setSourceFileName(str(body.get("sourceFileName")));
            req.setTargetType(FormatType.ofExt(str(body.get("target"))));
            req.setSourceType(FormatType.ofExt(str(body.get("sourceType"))));
            ConvertResult result = service.convert(req);
            if (!result.isSuccess()) return err(result.getMessage());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("text", result.getText());
            resp.put("targetType", result.getTargetType().ext);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return err("转换异常: " + e.getMessage());
        }
    }

    /** 异步任务状态 */
    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) return err("缺少 taskId");
        Map<String, Object> s = ConvertTaskManager.getStatus(taskId);
        if (s == null) return err("任务不存在或已过期");
        s.put("success", true);
        return JsonUtil.toJson(s);
    }

    /** 同步下载 */
    @RequestMapping("/download")
    public void download(String name) throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Path path = TempFileManager.resolve(name);
        if (path == null || !Files.exists(path)) {
            resp.setStatus(404, "Not Found");
            resp.send();
            return;
        }
        String fileName = path.getFileName().toString();
        String encoded = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        resp.setContentType("application/octet-stream");
        try (InputStream in = Files.newInputStream(path)) {
            resp.sendStatic(in, "application/octet-stream", Files.size(path));
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }

    private String err(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }
}