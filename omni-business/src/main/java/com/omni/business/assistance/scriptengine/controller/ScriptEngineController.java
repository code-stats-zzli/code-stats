package com.omni.business.assistance.scriptengine.controller;

import com.omni.business.assistance.scriptengine.service.ScriptExecutionService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** ★ L1-01 */
@Controller
@RequestMapping("/script/api")
public class ScriptEngineController extends BaseController {

    @Autowired
    private ScriptExecutionService executionService;

    @RequestMapping("/execute")
    @ResponseBody
    public String execute(@RequestBody Map<String, Object> params) {
        try {
            String output = executionService.execute(params);
            return okOne("output", output);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String uploadAndExecute() throws IOException {
        HttpServletRequest request = RequestContextHolder.getRequest();
        MultipartResolver resolver = request.getMultipartResolver();
        if (resolver == null) return error("请求不是 multipart 类型");

        FileItem fileItem = resolver.getFileItem("scriptFile");
        if (fileItem == null) return error("缺少文件");

        String language = resolver.getParameter("language");
        String executionMode = resolver.getParameter("executionMode");
        String paramsJson = resolver.getParameter("params");

        try {
            String scriptContent = new String(fileItem.getBytes(), StandardCharsets.UTF_8);
            Map<String, Object> params = new HashMap<>();
            params.put("scriptContent", scriptContent);
            params.put("language", language != null ? language : "python");
            params.put("executionMode", executionMode != null ? executionMode : "source");
            if (paramsJson != null && !paramsJson.isEmpty()) {
                Map<String, Object> parsed = JsonUtil.parseJson(paramsJson);
                if (parsed != null) params.putAll(parsed);
            }
            String output = executionService.execute(params);
            return okOne("output", output);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}