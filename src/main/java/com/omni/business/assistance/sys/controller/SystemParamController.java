package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.service.SystemParamService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统参数管理接口。
 *
 * <pre>
 * GET  /sys/api/params/list            → 可修改 / 不可修改参数
 * POST /sys/api/params/update          → 保存一个可修改参数 {key, value}
 * GET  /sys/api/params/export?format=  → 导出（omni-pack / file-block / merge-file / plain-json）
 * POST /sys/api/params/import          → 导入 {json, format}（format=auto 自动识别）
 * POST /sys/api/params/reload          → 从 system-params.json 重新加载到 ConfigLoader
 * </pre>
 */
@Controller
@RequestMapping("/sys/api/params")
public class SystemParamController {

    @Autowired
    private SystemParamService service;

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        return JsonUtil.toJson(service.list());
    }

    @RequestMapping("/update")
    @ResponseBody
    public String update(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        String key = str(req.get("key"));
        String value = str(req.get("value"));
        return JsonUtil.toJson(service.update(key, value));
    }

    @RequestMapping("/export")
    @ResponseBody
    public String export(@RequestParam(value = "format", required = false) String format) {
        return service.exportAsFormat(format);
    }

    @RequestMapping("/import")
    @ResponseBody
    public String importJson(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        Object raw = req.get("json");
        String content = raw == null ? JsonUtil.toJson(req) : String.valueOf(raw);
        String format = str(req.get("format"));
        return JsonUtil.toJson(service.importFromFormat(content, format));
    }

    @RequestMapping("/reload")
    @ResponseBody
    public String reload() {
        com.omni.framework.spring.config.ConfigLoader.reload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "已从 system-params.json 重新加载");
        return JsonUtil.toJson(out);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }
}