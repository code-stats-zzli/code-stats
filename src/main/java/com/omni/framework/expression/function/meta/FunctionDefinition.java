package com.omni.framework.expression.function.meta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个函数元数据（供前端函数库展示）。
 */
public class FunctionDefinition {

    private final String name;
    private final String signature;
    private final String description;
    private final String example;

    public FunctionDefinition(String name, String signature, String description, String example) {
        this.name = name;
        this.signature = signature;
        this.description = description;
        this.example = example;
    }

    public String getName() { return name; }
    public String getSignature() { return signature; }
    public String getDescription() { return description; }
    public String getExample() { return example; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("signature", signature);
        m.put("desc", description);
        m.put("example", example);
        return m;
    }
}