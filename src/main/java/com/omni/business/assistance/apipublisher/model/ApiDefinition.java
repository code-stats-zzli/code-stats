package com.omni.business.assistance.apipublisher.model;

import java.util.ArrayList;
import java.util.List;

public class ApiDefinition {
    private String id;
    private String name;
    private String description;
    private String path;
    private String method = "POST";
    private String sourceType = "table";
    private String datasourceId;
    private String database;
    private String table;
    private String backendUrl;
    private List<ApiParam> inputs = new ArrayList<>();
    private List<ApiParam> outputs = new ArrayList<>();
    private Integer pageEnabled = 0;
    private Integer pageSize = 20;
    private Integer status = 1;
    private String createTime;
    private String updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDatasourceId() { return datasourceId; }
    public void setDatasourceId(String datasourceId) { this.datasourceId = datasourceId; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getBackendUrl() { return backendUrl; }
    public void setBackendUrl(String backendUrl) { this.backendUrl = backendUrl; }
    public List<ApiParam> getInputs() { return inputs; }
    public void setInputs(List<ApiParam> inputs) { this.inputs = inputs; }
    public List<ApiParam> getOutputs() { return outputs; }
    public void setOutputs(List<ApiParam> outputs) { this.outputs = outputs; }
    public Integer getPageEnabled() { return pageEnabled; }
    public void setPageEnabled(Integer pageEnabled) { this.pageEnabled = pageEnabled; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}