// ======== src/main/java/com/omni/business/assistance/apipublisher/controller/ApiPublishController.java ========
package com.omni.business.assistance.apipublisher.controller;

import com.omni.business.assistance.apipublisher.model.ApiDefinition;
import com.omni.business.assistance.apipublisher.service.ApiPublishService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.swag.SwagHandler;
import com.omni.framework.swag.core.ApiDefinition.ApiParameter;
import com.omni.framework.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/publish/api")
public class ApiPublishController {

    @Autowired
    private ApiPublishService service;

    /** ★ 新增：支持的接口类型 */
    @RequestMapping("/types")
    @ResponseBody
    public String types() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(type("custom", "🔌 自定义接口", "调用任意外部 HTTP/HTTPS 接口"));
        list.add(type("table",  "📦 数据库表查询", "把数据库表暴露为 REST 接口"));
        list.add(type("system", "⚙️ 系统接口",     "把系统内部 Controller 接口暴露为对外 API"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("types", list);
        return JsonUtil.toJson(out);
    }

    /** ★ 新增：返回可选的系统接口列表（从 SwagHandler 提取） */
    @RequestMapping("/system-interfaces")
    @ResponseBody
    public String systemInterfaces() {
        try {
            List<SwagHandler.ModuleApiResult> modules = SwagHandler.getModuleApis();
            List<Map<String, Object>> out = new ArrayList<>();
            for (SwagHandler.ModuleApiResult m : modules) {
                List<Map<String, Object>> apis = new ArrayList<>();
                for (com.omni.framework.swag.core.ApiDefinition api : m.getApis()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("path", api.getPath());
                    item.put("method", api.getMethod());
                    item.put("summary", api.getSummary());
                    item.put("description", api.getDescription());
                    List<Map<String, Object>> params = new ArrayList<>();
                    for (ApiParameter p : api.getParameters()) {
                        Map<String, Object> pm = new LinkedHashMap<>();
                        pm.put("name", p.getName());
                        pm.put("in", p.getIn());
                        pm.put("required", p.isRequired());
                        pm.put("type", p.getType());
                        params.add(pm);
                    }
                    item.put("parameters", params);
                    apis.add(item);
                }
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("moduleName", m.getModuleName());
                mm.put("moduleDescription", m.getModuleDescription());
                mm.put("apis", apis);
                out.add(mm);
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("modules", out);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

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

    /** 保存/发布：按 apiType 分别校验 */
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
            String apiType = def.getApiType();
            if (apiType == null || apiType.isEmpty()) {
                apiType = "table".equals(def.getSourceType()) ? "table" : "custom";
                def.setApiType(apiType);
            }
            switch (apiType) {
                case "table":
                    if (isEmpty(def.getDatasourceId()) || isEmpty(def.getDatabase())
                            || isEmpty(def.getTable())) {
                        return err("数据库类型需要配置数据源 / 数据库 / 表");
                    }
                    break;
                case "custom":
                    if (isEmpty(def.getBackendUrl())) {
                        return err("自定义类型需要配置后端 URL");
                    }
                    break;
                case "system":
                    if (isEmpty(def.getSystemPath())) {
                        return err("系统类型需要配置系统接口路径");
                    }
                    break;
                default:
                    return err("不支持的 apiType: " + apiType);
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

    // ========== 工具 ==========
    private static Map<String, Object> type(String code, String label, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("desc", desc);
        return m;
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