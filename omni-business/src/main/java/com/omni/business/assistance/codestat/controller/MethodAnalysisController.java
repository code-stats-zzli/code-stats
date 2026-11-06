package com.omni.business.assistance.codestat.controller;

import com.omni.business.assistance.codestat.service.MethodAnalysisService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.codestats.dependency.model.MethodCallGraph;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.*;

/** ★ L1-01 */
@Controller
@RequestMapping("/code/api/method")
public class MethodAnalysisController extends BaseController {

    @Autowired
    private MethodAnalysisService service;

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
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/entries")
    @ResponseBody
    public String entries() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.listEntryPoints(g));
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/calltree")
    @ResponseBody
    public String calltree() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String entry = p.get("entry");
        if (entry == null || entry.isEmpty()) return error("缺少 entry 参数");
        int maxDepth = parseInt(p.get("maxDepth"), service.getDefaultMaxDepth());
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.buildCallTreeJson(g, entry, maxDepth));
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/sequence")
    @ResponseBody
    public String sequence() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String entry = p.get("entry");
        if (entry == null || entry.isEmpty()) return "@startuml\nnote \"缺少 entry\"\n@enduml";
        int maxDepth = parseInt(p.get("maxDepth"), service.getDefaultMaxDepth());
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return service.generateSequence(g, entry, maxDepth);
        } catch (Exception e) { return "@startuml\nnote \"" + e.getMessage() + "\"\n@enduml"; }
    }

    @RequestMapping("/neighbors")
    @ResponseBody
    public String neighbors() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String methodId = p.get("methodId");
        if (methodId == null || methodId.isEmpty()) return error("缺少 methodId");
        try {
            MethodCallGraph g = service.getOrBuild(p.get("rootDir"));
            return JsonUtil.toJson(service.getNeighbors(g, methodId));
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/clearCache")
    @ResponseBody
    public String clearCache() {
        service.clearCache();
        return ok();
    }

    private int parseInt(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}