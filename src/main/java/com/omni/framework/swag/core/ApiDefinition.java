package com.omni.framework.swag.core;

import java.util.ArrayList;
import java.util.List;

public class ApiDefinition {
    private String id;
    private String path;
    private String method;          // GET / POST
    private String summary;
    private String description;
    private List<ApiParameter> parameters = new ArrayList<>();
    private boolean requestBody;    // 是否需要 JSON Body

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ApiParameter> getParameters() { return parameters; }
    public void setParameters(List<ApiParameter> parameters) { this.parameters = parameters; }
    public boolean isRequestBody() { return requestBody; }
    public void setRequestBody(boolean requestBody) { this.requestBody = requestBody; }

    public static class ApiParameter {
        private String name;
        private String in;          // "query", "path"
        private boolean required;
        private String type;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIn() { return in; }
        public void setIn(String in) { this.in = in; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}