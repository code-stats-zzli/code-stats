package com.omni.framework.datacube.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一查询结果契约（任何 provider 输出此结构） */
public class CubeQueryResult {
    private boolean success;
    private List<String> columns = new ArrayList<>();
    private List<List<Object>> rows = new ArrayList<>();
    private Map<String, Object> meta = new LinkedHashMap<>();

    public static CubeQueryResult ok(List<String> cols, List<List<Object>> rows,
                                     Map<String, Object> meta) {
        CubeQueryResult r = new CubeQueryResult();
        r.success = true;
        r.columns = cols != null ? cols : new ArrayList<>();
        r.rows = rows != null ? rows : new ArrayList<>();
        r.meta = meta != null ? meta : new LinkedHashMap<>();
        return r;
    }

    public static CubeQueryResult error(String msg) {
        CubeQueryResult r = new CubeQueryResult();
        r.success = false;
        r.meta.put("error", msg);
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public List<List<Object>> getRows() { return rows; }
    public void setRows(List<List<Object>> rows) { this.rows = rows; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
}