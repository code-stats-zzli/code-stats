package com.omni.framework.datacube.model;

import java.util.Map;

/** 数据立方定义（架构层 DTO，可被任意业务层复用） */
public class DataCube {
    private String id;
    private String name;
    private CubeType type;
    private Map<String, Object> config;       // provider 特有配置
    private Map<String, Object> params;       // 默认参数
    private CubeSchema schema;                // 探测到的元数据（可空）
    private Map<String, Object> cache;        // {enabled, ttlMs}
    private String description;
    private Long createdAt;
    private Long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CubeType getType() { return type; }
    public void setType(CubeType type) { this.type = type; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public CubeSchema getSchema() { return schema; }
    public void setSchema(CubeSchema schema) { this.schema = schema; }
    public Map<String, Object> getCache() { return cache; }
    public void setCache(Map<String, Object> cache) { this.cache = cache; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}