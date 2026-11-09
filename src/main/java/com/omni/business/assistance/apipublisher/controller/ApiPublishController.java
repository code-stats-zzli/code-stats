package com.omni.business.assistance.apipublisher.controller;

import com.omni.business.assistance.apipublisher.model.ApiDefinition;
import com.omni.business.assistance.apipublisher.service.ApiPublishService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/publish/api")
public class ApiPublishController {

    @Autowired
    private ApiPublishService service;

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        try {
            List<ApiDefinition> list = service.listAll();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("items", list);
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    @RequestMapping("/get")
    @ResponseBody
    public String get(@RequestParam("id") String id) {
        try {
            ApiDefinition def = service.getById(id);
            Map<String, Object> out = new LinkedHashMap<>();
            if (def == null) {
                out.put("success", false);
                out.put("error", "API 不存在: " + id);
            } else {
                out.put("success", true);
                out.put("data", def);
            }
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    @RequestMapping("/save")
    @ResponseBody
    public String save(@RequestBody ApiDefinition def) {
        try {
            if (def == null) return err("请求体为空");
            if (def.getName() == null || def.getName().trim().isEmpty()) {
                return err("API 名称不能为空");
            }
            if (def.getPath() == null || !def.getPath().startsWith("/")) {
                return err("API 路径必须以 / 开头");
            }
            if ("table".equals(def.getSourceType())) {
                if (isEmpty(def.getDatasourceId()) || isEmpty(def.getDatabase())
                        || isEmpty(def.getTable())) {
                    return err("数据源 / 数据库 / 表 不能为空");
                }
            }
            String id = service.save(def);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("id", id);
            out.put("message", "API 已发布，调用入口：POST /api/gateway/execute");
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(@RequestBody Map<String, Object> req) {
        try {
            String id = req == null ? null : str(req.get("id"));
            if (isEmpty(id)) return err("缺少 id");
            boolean ok = service.delete(id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", ok);
            if (!ok) out.put("error", "API 不存在");
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    @RequestMapping("/toggle")
    @ResponseBody
    public String toggle(@RequestBody Map<String, Object> req) {
        try {
            String id = req == null ? null : str(req.get("id"));
            if (isEmpty(id)) return err("缺少 id");
            Integer status = req.get("status") == null ? 1
                    : Integer.parseInt(String.valueOf(req.get("status")));
            ApiDefinition def = service.toggle(id, status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", def != null);
            if (def != null) out.put("status", def.getStatus());
            else out.put("error", "API 不存在");
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private String err(String msg) {
        Map<String, Object> out = new HashMap<>();
        out.put("success", false);
        out.put("error", msg == null ? "未知错误" : msg);
        return JsonUtil.toJson(out);
    }
}