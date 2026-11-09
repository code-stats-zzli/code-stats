package com.omni.business.assistance.apipublisher.model;

/**
 * 单个参数定义（入参 / 出参共用）。
 */
public class ApiParam {
    private String name;
    private String type = "string";
    private String in = "query";
    private boolean required = false;
    private String defaultValue;
    private String description;
    private String mapTo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getIn() { return in; }
    public void setIn(String in) { this.in = in; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMapTo() { return mapTo; }
    public void setMapTo(String mapTo) { this.mapTo = mapTo; }
}