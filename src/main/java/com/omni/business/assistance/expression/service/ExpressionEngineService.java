package com.omni.business.assistance.expression.service;

import com.omni.common.expression.ExpressionTextUtils;
import com.omni.common.expression.ExpressionValueUtils;
import com.omni.framework.expression.ExpressionEngine;
import com.omni.framework.expression.function.ExtendedFunctionRegistry;
import com.omni.framework.expression.function.meta.FunctionCategory;
import com.omni.framework.spring.annotation.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpressionEngineService {

    static {
        ExtendedFunctionRegistry.registerDefaults();
    }

    public Map<String, Object> evaluate(String expression, Map<String, Object> variables) {
        long start = System.currentTimeMillis();
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            if (expression == null || expression.trim().isEmpty()) {
                resp.put("success", false);
                resp.put("error", "表达式不能为空");
                resp.put("durationMs", 0);
                return resp;
            }
            String cleaned = ExpressionTextUtils.stripDollarPrefix(expression);
            Map<String, Object> vars = ExpressionTextUtils.normalizeDollarVariables(variables);

            Object result = ExpressionEngine.eval(cleaned, vars);

            resp.put("success", true);
            resp.put("result", ExpressionValueUtils.toDisplay(result));
            resp.put("resultType", ExpressionValueUtils.simpleTypeName(result));
            resp.put("cleanedExpression", cleaned);
            resp.put("variables", vars);
            resp.put("durationMs", System.currentTimeMillis() - start);
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            String msg = root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : ": " + root.getMessage());
            resp.put("success", false);
            resp.put("error", msg);
            resp.put("durationMs", System.currentTimeMillis() - start);
        }
        return resp;
    }

    public List<Map<String, Object>> listFunctionMeta() {
        List<FunctionCategory> categories = ExtendedFunctionRegistry.listCategories();
        List<Map<String, Object>> out = new ArrayList<>(categories.size());
        for (FunctionCategory c : categories) out.add(c.toMap());
        return out;
    }
}