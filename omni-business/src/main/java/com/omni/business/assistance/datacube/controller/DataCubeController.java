package com.omni.business.assistance.datacube.controller;

import com.omni.business.assistance.datacube.service.DataCubeMetadataService;
import com.omni.business.assistance.datacube.service.DataCubeService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.datacube.model.*;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.*;

/** ★ L1-01 */
@Controller
@RequestMapping("/cube/api")
public class DataCubeController extends BaseController {

    @Autowired private DataCubeService cubeService;
    @Autowired private DataCubeMetadataService metaService;

    @RequestMapping("/datasources")
    @ResponseBody
    public String datasources() {
        try { return JsonUtil.toJson(metaService.listDataSources()); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/databases")
    @ResponseBody
    public String databases() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dsId = p.get("dsId");
        if (dsId == null) return error("缺少 dsId");
        try { return JsonUtil.toJson(metaService.listDatabases(dsId)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/tables")
    @ResponseBody
    public String tables() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dsId = p.get("dsId"), db = p.get("db");
        if (dsId == null || db == null) return error("缺少 dsId 或 db");
        try { return JsonUtil.toJson(metaService.listTables(dsId, db)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/columns")
    @ResponseBody
    public String columns() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dsId = p.get("dsId"), db = p.get("db"), table = p.get("table");
        if (dsId == null || db == null || table == null) return error("缺少 dsId / db / table");
        try { return JsonUtil.toJson(metaService.describeColumns(dsId, db, table)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/list")
    @ResponseBody
    public String list() { return JsonUtil.toJson(cubeService.list()); }

    @RequestMapping("/get")
    @ResponseBody
    public String get() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String id = p.get("id");
        if (id == null) return error("缺少 id");
        DataCube c = cubeService.getById(id);
        return c == null ? error("cube 不存在") : JsonUtil.toJson(c);
    }

    @RequestMapping("/save")
    @ResponseBody
    public String save(@RequestBody DataCube cube) {
        try { return JsonUtil.toJson(cubeService.save(cube)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(@RequestBody Map<String, Object> req) {
        String id = req.get("id") != null ? req.get("id").toString() : null;
        if (id == null) return error("缺少 id");
        return okOne("deleted", cubeService.delete(id));
    }

    @RequestMapping("/query")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String query(@RequestBody Map<String, Object> req) {
        try {
            String cubeId = (String) req.get("cubeId");
            Map<String, Object> params = (Map<String, Object>) req.getOrDefault("params", new HashMap<>());
            if (cubeId == null) return error("缺少 cubeId");
            return JsonUtil.toJson(cubeService.query(cubeId, params));
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/preview")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String preview(@RequestBody Map<String, Object> req) {
        try {
            DataCube cube = new DataCube();
            String typeStr = (String) req.getOrDefault("type", "TABLE");
            cube.setType(CubeType.valueOf(typeStr.toUpperCase()));
            cube.setConfig((Map<String, Object>) req.get("config"));
            Map<String, Object> params = (Map<String, Object>) req.getOrDefault("params", new HashMap<>());
            return JsonUtil.toJson(cubeService.preview(cube, params));
        } catch (Exception e) { return error(e.getMessage()); }
    }

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
                    Map<String, Object> params = (Map<String, Object>) r.getOrDefault("params", new HashMap<>());
                    results.add(cubeService.query(cubeId, params));
                }
            }
            return okOne("results", results);
        } catch (Exception e) { return error(e.getMessage()); }
    }
}