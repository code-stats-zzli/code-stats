package com.omni.framework.datacube.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 立方元数据：列定义 + 附加信息 */
public class CubeSchema {
    private List<ColumnMeta> columns = new ArrayList<>();
    private Map<String, Object> extra = new LinkedHashMap<>();

    public List<ColumnMeta> getColumns() { return columns; }
    public void setColumns(List<ColumnMeta> columns) { this.columns = columns; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}