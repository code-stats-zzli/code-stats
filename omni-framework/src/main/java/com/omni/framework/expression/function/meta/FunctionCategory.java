package com.omni.framework.expression.function.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数分类元数据（一个分类下挂多个 FunctionDefinition）。
 */
public class FunctionCategory {

    private final String category;
    private final String title;
    private final String description;
    private final List<FunctionDefinition> functions;

    public FunctionCategory(String category, String title, String description,
                            List<FunctionDefinition> functions) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.functions = functions != null ? functions : new ArrayList<>();
    }

    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<FunctionDefinition> getFunctions() { return functions; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("title", title);
        m.put("desc", description);
        List<Map<String, Object>> fns = new ArrayList<>(functions.size());
        for (FunctionDefinition f : functions) fns.add(f.toMap());
        m.put("functions", fns);
        return m;
    }
}