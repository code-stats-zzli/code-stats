package com.omni.business.assistance.codestat.controller;

import com.omni.business.assistance.codestat.service.MethodAnalysisService;

import com.omni.framework.codestats.dependency.model.MethodCallGraph;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.*;

/**
 * 方法级调用链分析接口。
 *
 * <pre>
 * GET /code/api/method/config      → 返回默认配置
 * GET /code/api/method/list        ?rootDir=...
 * GET /code/api/method/entries     ?rootDir=...
 * GET /code/api/method/calltree    ?rootDir=...&entry=...&maxDepth=5
 * GET /code/api/method/sequence    ?rootDir=...&entry=...&maxDepth=5   → PlantUML 文本
 * GET /code/api/method/neighbors   ?rootDir=...&methodId=...
 * GET /code/api/method/clearCache
 * </pre>
 */
@Controller
@RequestMapping("/code/api/method")
public class MethodAnalysisController {

    @Autowired
    private MethodAnalysisService service;

    /** ★ 新增：返回默认配置，供前端初始化输入框 */
    @RequestMapping("/config")
    @ResponseBody
    public String config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaultRootDir", service.getDefaultRootDir());
        m.put("defaultMaxDepth", service.getDefaultMaxDepth());
        m.put("excludePaths", service.getExcludePaths());
        m.put("excludeTest", service.isExcludeTest());
        m.put("cacheTtlMs", service.getCacheTtlMs());
        return JsonUtil.toJson(m);
    }

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.listMethods(g));
        } catch (Exception e) {
            return err(e);
        }
    }

    @RequestMapping("/entries")
    @ResponseBody
    public String entries() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.listEntryPoints(g));
        } catch (Exception e) {
            return err(e);
        }
    }

    @RequestMapping("/calltree")
    @ResponseBody
    public String calltree() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String entry = p.get("entry");
        if (entry == null || entry.isEmpty()) {
            return err(new IllegalArgumentException("缺少 entry 参数"));
        }
        int maxDepth = parseInt(p.get("maxDepth"), service.getDefaultMaxDepth());
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.buildCallTreeJson(g, entry, maxDepth));
        } catch (Exception e) {
            return err(e);
        }
    }

    @RequestMapping("/sequence")
    @ResponseBody
    public String sequence() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String entry = p.get("entry");
        if (entry == null || entry.isEmpty()) {
            return "@startuml\nnote \"缺少 entry\"\n@enduml";
        }
        int maxDepth = parseInt(p.get("maxDepth"), service.getDefaultMaxDepth());
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return service.generateSequence(g, entry, maxDepth);
        } catch (Exception e) {
            return "@startuml\nnote \"" + e.getMessage() + "\"\n@enduml";
        }
    }

    @RequestMapping("/neighbors")
    @ResponseBody
    public String neighbors() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String methodId = p.get("methodId");
        if (methodId == null || methodId.isEmpty()) {
            return err(new IllegalArgumentException("缺少 methodId"));
        }
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.getNeighbors(g, methodId));
        } catch (Exception e) {
            return err(e);
        }
    }

    @RequestMapping("/clearCache")
    @ResponseBody
    public String clearCache() {
        service.clearCache();
        return JsonUtil.toJson(Collections.singletonMap("success", true));
    }

    // ==================== 工具 ====================

    private String err(Exception e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e == null ? "未知错误" : e.getMessage());
        return JsonUtil.toJson(m);
    }

    private int parseInt(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}