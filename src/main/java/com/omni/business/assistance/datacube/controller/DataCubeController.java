package com.omni.business.assistance.datacube.controller;

import com.omni.business.assistance.datacube.service.DataCubeMetadataService;
import com.omni.business.assistance.datacube.service.DataCubeService;
import com.omni.framework.datacube.model.*;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.*;

/**
 * 数据立方 REST 接口 — 元数据 / 数据双通道
 */
@Controller
@RequestMapping("/cube/api")
public class DataCubeController {

    @Autowired private DataCubeService cubeService;
    @Autowired private DataCubeMetadataService metaService;

    // ==================== 元数据 ====================

    /** 数据源列表 */
    @RequestMapping("/datasources")
    @ResponseBody
    public String datasources() {
        try { return JsonUtil.toJson(metaService.listDataSources()); }
        catch (Exception e) { return err(e); }
    }

    /** 数据库列表 */
    @RequestMapping("/databases")
    @ResponseBody
    public String databases() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dsId = p.get("dsId");
        if (dsId == null) return err("缺少 dsId");
        try { return JsonUtil.toJson(metaService.listDatabases(dsId)); }
        catch (Exception e) { return err(e); }
    }

    /** 表列表 */
    @RequestMapping("/tables")
    @ResponseBody
    public String tables() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dsId = p.get("dsId");
        String db = p.get("db");
        if (dsId == null || db == null) return err("缺少 dsId 或 db");
        try { return JsonUtil.toJson(metaService.listTables(dsId, db)); }
        catch (Exception e) { return err(e); }
    }

    /** 列元数据 */
    @RequestMapping("/columns")
    @ResponseBody
    public String columns() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dsId = p.get("dsId");
        String db = p.get("db");
        String table = p.get("table");
        if (dsId == null || db == null || table == null)
            return err("缺少 dsId / db / table");
        try { return JsonUtil.toJson(metaService.describeColumns(dsId, db, table)); }
        catch (Exception e) { return err(e); }
    }

    // ==================== Cube CRUD ====================

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        return JsonUtil.toJson(cubeService.list());
    }

    @RequestMapping("/get")
    @ResponseBody
    public String get() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String id = p.get("id");
        if (id == null) return err("缺少 id");
        DataCube c = cubeService.getById(id);
        return c == null ? err("cube 不存在") : JsonUtil.toJson(c);
    }

    @RequestMapping("/save")
    @ResponseBody
    public String save(@RequestBody DataCube cube) {
        try {
            return JsonUtil.toJson(cubeService.save(cube));
        } catch (Exception e) { return err(e); }
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(@RequestBody Map<String, Object> req) {
        String id = req.get("id") != null ? req.get("id").toString() : null;
        if (id == null) return err("缺少 id");
        return JsonUtil.toJson(Map.of("success", cubeService.delete(id)));
    }

    // ==================== 数据查询 ====================

    /** 完整执行（cube 已持久化） */
    @RequestMapping("/query")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String query(@RequestBody Map<String, Object> req) {
        try {
            String cubeId = (String) req.get("cubeId");
            Map<String, Object> params = (Map<String, Object>)
                    req.getOrDefault("params", new HashMap<>());
            if (cubeId == null) return err("缺少 cubeId");
            return JsonUtil.toJson(cubeService.query(cubeId, params));
        } catch (Exception e) { return err(e); }
    }

    /** 临时预览（不落库）— 前端拖拽首选 */
    @RequestMapping("/preview")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String preview(@RequestBody Map<String, Object> req) {
        try {
            DataCube cube = new DataCube();
            String typeStr = (String) req.getOrDefault("type", "TABLE");
            cube.setType(CubeType.valueOf(typeStr.toUpperCase()));
            cube.setConfig((Map<String, Object>) req.get("config"));
            Map<String, Object> params = (Map<String, Object>)
                    req.getOrDefault("params", new HashMap<>());
            return JsonUtil.toJson(cubeService.preview(cube, params));
        } catch (Exception e) { return err(e); }
    }

    /** 批量执行（页面同时加载多个 cube） */
    @RequestMapping("/batchQuery")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String batchQuery(@RequestBody Map<String, Object> req) {
        try {
            List<Map<String, Object>> requests = (List<Map<String, Object>>) req.get("requests");
            List<CubeQueryResult> results = new ArrayList<>();
            if (requests != null) {
                for (Map<String, Object> r : requests) {
                    String cubeId = (String) r.get("cubeId");
                    Map<String, Object> params = (Map<String, Object>)
                            r.getOrDefault("params", new HashMap<>());
                    results.add(cubeService.query(cubeId, params));
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("results", results);
            return JsonUtil.toJson(out);
        } catch (Exception e) { return err(e); }
    }

    private String err(Exception e) {
        return JsonUtil.toJson(Map.of("success", false,
                "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }

    private String err(String msg) {
        return JsonUtil.toJson(Map.of("success", false, "error", msg));
    }
}