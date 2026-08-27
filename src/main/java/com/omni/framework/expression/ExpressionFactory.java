package com.omni.framework.expression;

import com.omni.framework.expression.function.BuiltinFunctions;
import com.omni.framework.expression.function.FunctionResolver;
import com.omni.framework.expression.resolver.VariableResolver;
import com.omni.framework.expression.resolver.impl.*;

import java.util.*;

/**
 * 表达式引擎一站式静态工具类。
 *
 * <h2>默认支持的解析器配置（__resolvers）</h2>
 * <ul>
 *   <li><b>root</b> → {@link RootObjectResolver}：POJO 属性映射</li>
 *   <li><b>table</b> → {@link TabularDataResolver} 或 {@link SwitchableRowsResolver}</li>
 *
 *   <li><b>functions</b> → 临时函数注册</li>
 *   <li><b>custom</b> → 用户自定义解析器列表（含 ColumnMappingResolver 等）</li>
 * </ul>
 * 普通键（非 __resolvers）作为显式变量，优先级最高。
 */
public final class ExpressionFactory {

    private static final ExpressionParser PARSER = new StandardExpressionParser();

    // ==================== 统一配置键 ====================
    private static final String KEY_RESOLVERS_CONFIG = "__resolvers";

    // 解析器配置子键
    private static final String CFG_ROOT = "root";
    private static final String CFG_TABLE = "table";
    private static final String CFG_CLOUMN_MAP = "cloumnmap";           // ★ 新增：Map 数据源
    private static final String CFG_FUNCTIONS = "functions";
    private static final String CFG_CUSTOM = "custom";

    // 表格配置子键
    private static final String TABLE_HEADERS = "headers";
    private static final String TABLE_ROW = "row";
    private static final String TABLE_ROWS = "rows";
    private static final String TABLE_ROW_INDEX = "rowIndex";

    private ExpressionFactory() {}

    // ==================== 核心方法 ====================



    public static Object eval(String expression, EvaluationContext context) {
        if (context == null) {
            context = buildContextFromData(null);
        }
        return PARSER.parse(expression).getValue(context);
    }

    // ==================== 向后兼容的便捷方法 ====================

    public static Object eval(String expression, Object rootObject) {
        Map<String, Object> data = new HashMap<>();
        if (rootObject != null) {
            data.put(KEY_RESOLVERS_CONFIG, Map.of(CFG_ROOT, rootObject));
        }
        return eval(expression, data);
    }



    public static Object eval(String expression, Map<String, Object> variables, Object rootObject) {
        Map<String, Object> data = new HashMap<>(variables);
        if (rootObject != null) {
            Map<String, Object> resolvers = new HashMap<>();
            resolvers.put(CFG_ROOT, rootObject);
            data.put(KEY_RESOLVERS_CONFIG, resolvers);
        }
        return eval(expression, data);
    }

    public static Object eval(String expression, Map<String, String> rowData, List<String> headers) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> resolvers = new HashMap<>();
        resolvers.put(CFG_CLOUMN_MAP, rowData);
        if (headers != null) {
            Map<String, Object> tableConfig = new HashMap<>();
            tableConfig.put(TABLE_HEADERS, headers);
            resolvers.put(CFG_TABLE, tableConfig);
        }
        data.put(KEY_RESOLVERS_CONFIG, resolvers);
        return eval(expression, data);
    }

    public static Object eval(String expression, List<String> headers, Object[] rowData) {
        return eval(expression, headers, rowData, null);
    }

    public static Object eval(String expression, List<String> headers, Object[] rowData, Map<String, Object> extraVars) {
        Map<String, Object> data = new HashMap<>();
        if (extraVars != null) data.putAll(extraVars);
        Map<String, Object> tableConfig = new HashMap<>();
        tableConfig.put(TABLE_HEADERS, headers);
        tableConfig.put(TABLE_ROW, rowData);
        data.put(KEY_RESOLVERS_CONFIG, Map.of(CFG_TABLE, tableConfig));
        return eval(expression, data);
    }

    public static Object eval(String expression, List<String> headers, List<Object[]> rows, int rowIndex) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> tableConfig = new HashMap<>();
        tableConfig.put(TABLE_HEADERS, headers);
        tableConfig.put(TABLE_ROWS, rows);
        tableConfig.put(TABLE_ROW_INDEX, rowIndex);
        data.put(KEY_RESOLVERS_CONFIG, Map.of(CFG_TABLE, tableConfig));
        return eval(expression, data);
    }

    public static Object eval(String expression, List<VariableResolver> customResolvers) {
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_RESOLVERS_CONFIG, Map.of(CFG_CUSTOM, customResolvers));
        return eval(expression, data);
    }

    public static Object eval(String expression, Object rootObject, Map<String, FunctionResolver> functions) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> resolvers = new HashMap<>();
        if (rootObject != null) resolvers.put(CFG_ROOT, rootObject);
        if (functions != null) resolvers.put(CFG_FUNCTIONS, functions);
        data.put(KEY_RESOLVERS_CONFIG, resolvers);
        return eval(expression, data);
    }

    // ==================== 全局函数注册 ====================

    public static void registerGlobalFunction(String name, FunctionResolver resolver) {
        GlobalFunctionHolder.register(name, resolver);
    }

    // ==================== 预解析 ====================

    public static Expression parse(String expression) {
        return PARSER.parse(expression);
    }

    public static Object eval(Expression expr, Map<String, Object> data) {
        return expr.getValue(buildContextFromData(data));
    }

    public static Object eval(Expression expr, Object rootObject) {
        Map<String, Object> data = new HashMap<>();
        if (rootObject != null) {
            data.put(KEY_RESOLVERS_CONFIG, Map.of(CFG_ROOT, rootObject));
        }
        return eval(expr, data);
    }

    // ==================== 内部辅助 ====================

    @SuppressWarnings("unchecked")
    private static VariableAwareEvaluationContext buildContextFromData(Map<String, Object> data) {
        VariableAwareEvaluationContext context = new VariableAwareEvaluationContext();
        BuiltinFunctions.registerAll(context);
        GlobalFunctionHolder.injectTo(context);

        if (data == null || data.isEmpty()) {
            return context;
        }

        Object resolversConfig = data.get(KEY_RESOLVERS_CONFIG);
        if (resolversConfig instanceof Map) {
            processResolversConfig(context, (Map<String, Object>) resolversConfig);
        }

        // ★ 显式变量：非 __resolvers 的键自动注册为显式变量（优先级最高）
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!KEY_RESOLVERS_CONFIG.equals(entry.getKey())) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }

        return context;
    }

    @SuppressWarnings("unchecked")
    private static void processResolversConfig(VariableAwareEvaluationContext context, Map<String, Object> config) {

        // 1. 自定义解析器
        if (config.containsKey(CFG_CUSTOM)) {
            Object custom = config.get(CFG_CUSTOM);
            if (custom instanceof List) {
                for (Object r : (List<?>) custom) {
                    if (r instanceof VariableResolver) {
                        context.addResolver((VariableResolver) r);
                    }
                }
            }
        }

        // 2. 根对象
        if (config.containsKey(CFG_ROOT)) {
            Object root = config.get(CFG_ROOT);
            if (root != null) {
                context.addResolver(new RootObjectResolver(root));
            }
        }

        // 3. ★ Map 数据源（RowDataResolver）
        if (config.containsKey(CFG_CLOUMN_MAP)) {
            Object mapObj = config.get(CFG_CLOUMN_MAP);
            if (mapObj instanceof Map) {
                context.addResolver(new ColumnMappingResolver((Map<String, String>) mapObj));
            }
        }

        // 4. 表格数据
        if (config.containsKey(CFG_TABLE)) {
            Object tableObj = config.get(CFG_TABLE);
            if (tableObj instanceof Map) {
                processTableConfig(context, (Map<String, Object>) tableObj);
            }
        }

        // 5. 临时函数
        if (config.containsKey(CFG_FUNCTIONS)) {
            Object funcs = config.get(CFG_FUNCTIONS);
            if (funcs instanceof Map) {
                ((Map<String, FunctionResolver>) funcs).forEach(context::registerFunction);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void processTableConfig(VariableAwareEvaluationContext context, Map<String, Object> tableConfig) {
        List<String> headers = null;
        if (tableConfig.containsKey(TABLE_HEADERS)) {
            Object h = tableConfig.get(TABLE_HEADERS);
            if (h instanceof List) {
                headers = (List<String>) h;
            } else if (h instanceof String[]) {
                headers = Arrays.asList((String[]) h);
            }
        }
        if (headers == null || headers.isEmpty()) {
            return;
        }

        if (tableConfig.containsKey(TABLE_ROW)) {
            Object[] rowArray = toObjectArray(tableConfig.get(TABLE_ROW));
            if (rowArray != null) {
                context.addResolver(new TabularDataResolver(headers, rowArray));
            }
        }

        if (tableConfig.containsKey(TABLE_ROWS)) {
            Object rowsObj = tableConfig.get(TABLE_ROWS);
            if (rowsObj instanceof List) {
                List<Object[]> rowList = new ArrayList<>();
                for (Object item : (List<?>) rowsObj) {
                    Object[] arr = toObjectArray(item);
                    if (arr != null) rowList.add(arr);
                }
                if (!rowList.isEmpty()) {
                    SwitchableRowsResolver resolver = new SwitchableRowsResolver(headers, rowList);
                    if (tableConfig.containsKey(TABLE_ROW_INDEX)) {
                        Object idx = tableConfig.get(TABLE_ROW_INDEX);
                        if (idx instanceof Number) {
                            resolver.setRowIndex(((Number) idx).intValue());
                        }
                    }
                    context.addResolver(resolver);
                }
            }
        }
    }

    private static Object[] toObjectArray(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Object[]) return (Object[]) obj;
        if (obj instanceof List) return ((List<?>) obj).toArray();
        return null;
    }

    // ==================== 内部类：全局函数持有 ====================

    private static class GlobalFunctionHolder {
        private static final Map<String, FunctionResolver> GLOBAL_FUNCTIONS = new HashMap<>();

        static synchronized void register(String name, FunctionResolver resolver) {
            if (resolver != null) {
                GLOBAL_FUNCTIONS.put(name, resolver);
            }
        }

        static void injectTo(EvaluationContext context) {
            GLOBAL_FUNCTIONS.forEach(context::registerFunction);
        }
    }
}