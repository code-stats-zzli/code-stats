package com.omni.business.assistance.formatconverter.controller;

import com.omni.business.assistance.formatconverter.service.ConvertTaskManager;
import com.omni.business.assistance.formatconverter.service.FormatConverterService;
import com.omni.business.common.converter.TempFileManager;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.ResponseHelper;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** ★ L1-01 + L2-01 */
@Controller
@RequestMapping("/converter/api")
public class FormatConverterController extends BaseController {

    @Autowired
    private FormatConverterService service;

    @RequestMapping("/supported")
    @ResponseBody
    public String supported() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("converters", service.getRegistry().listSupported());
        return okFlat(resp);
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        MultipartResolver mp = RequestContextHolder.getRequest().getMultipartResolver();
        if (mp == null) return error("请求不是 multipart");
        FileItem fileItem = mp.getFileItem("file");
        if (fileItem == null) return error("缺少 file 字段");
        String targetExt = mp.getParameter("target");
        boolean async = "true".equalsIgnoreCase(mp.getParameter("async"));
        try {
            ConvertRequest req = new ConvertRequest();
            req.setSourceFileName(fileItem.getFileName());
            req.setSourceBytes(fileItem.getBytes());
            req.setTargetType(FormatType.ofExt(targetExt));
            if (req.getTargetType() == null) return error("无效的目标格式: " + targetExt);
            String srcExt = mp.getParameter("sourceType");
            if (srcExt != null && !srcExt.isEmpty()) req.setSourceType(FormatType.ofExt(srcExt));

            if (async) {
                String taskId = ConvertTaskManager.submit(req, service::convert);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("async", true);
                r.put("taskId", taskId);
                return okFlat(r);
            }
            ConvertResult result = service.convert(req);
            if (!result.isSuccess()) return error(result.getMessage());
            String storedName = TempFileManager.store(result);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("storedName", storedName);
            resp.put("fileName", result.getSuggestedFileName());
            resp.put("targetType", result.getTargetType().ext);
            resp.put("size", result.size());
            resp.put("meta", result.getMeta());
            resp.put("downloadUrl", "/converter/api/download?name="
                    + java.net.URLEncoder.encode(storedName, "UTF-8"));
            return okFlat(resp);
        } catch (Exception e) {
            return error("上传转换失败: " + e.getMessage());
        }
    }

    @RequestMapping("/text")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String text(@RequestBody Map<String, Object> body) {
        if (body == null) return error("请求体为空");
        try {
            ConvertRequest req = new ConvertRequest();
            req.setSourceText(str(body.get("source")));
            req.setSourceFileName(str(body.get("sourceFileName")));
            req.setTargetType(FormatType.ofExt(str(body.get("target"))));
            req.setSourceType(FormatType.ofExt(str(body.get("sourceType"))));
            ConvertResult result = service.convert(req);
            if (!result.isSuccess()) return error(result.getMessage());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("text", result.getText());
            resp.put("targetType", result.getTargetType().ext);
            return okFlat(resp);
        } catch (Exception e) {
            return error("转换异常: " + e.getMessage());
        }
    }

    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) return error("缺少 taskId");
        Map<String, Object> s = ConvertTaskManager.getStatus(taskId);
        if (s == null) return error("任务不存在或已过期");
        return okFlat(s);
    }

    @RequestMapping("/download")
    public void download(String name) throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Path path = TempFileManager.resolve(name);
        if (path == null || !Files.exists(path)) {
            ResponseHelper.writeError(resp, 404, "文件不存在");
            return;
        }
        ResponseHelper.writeAttachment(resp, path, path.getFileName().toString());
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
}