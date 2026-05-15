package com.omni.framework.v4j.component;



import com.omni.framework.v4j.core.ReactiveData;
import com.omni.framework.v4j.core.Watcher;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 模拟 Vue 单文件组件（SFC）
 * 包含 data、props、template、methods、生命周期
 */
public abstract class Component {
    protected ReactiveData data;
    protected final Map<String, Object> props;
    protected final Map<String, Function<Object[], Object>> methods = new HashMap<>();
    protected String template;          // 简单模板字符串
    protected Watcher renderWatcher;
    protected Style style;              // 组件样式对象

    // 生命周期钩子（可选）
    protected Runnable beforeCreate = () -> {};
    protected Runnable created = () -> {};
    protected Runnable beforeMount = () -> {};
    protected Runnable mounted = () -> {};
    protected Runnable beforeUpdate = () -> {};
    protected Runnable updated = () -> {};

    public Component(Map<String, Object> props) {
        this.props = props != null ? props : new HashMap<>();
        beforeCreate.run();
        initData();
        initMethods();
        initStyle();
        created.run();
        // 建立渲染 Watcher
        if (template != null && !template.isEmpty()) {
            renderWatcher = new Watcher(this::render);
        }
    }

    // 子类必须实现：返回初始 data 的 Map
    protected abstract Map<String, Object> data();

    // 可选：定义方法
    protected void initMethods() {}

    // 定义样式（子类覆盖）
    protected Style style() {
        return new Style();
    }

    private void initData() {
        Map<String, Object> rawData = data();
        if (rawData == null) rawData = new HashMap<>();
        // 合并 props 到 data？简单起见，props 单独存储，但可以在模板中通过 this.props 访问
        // 我们统一将 data 作为响应式对象
        this.data = new ReactiveData(rawData);
    }

    private void initStyle() {
        this.style = style();
    }

    /**
     * 渲染模板（将模板字符串与数据结合，输出虚拟 DOM 字符串）
     * 这里简化为控制台打印，实际可扩展为构建真正的 DOM 树
     */
    public void render() {
        beforeUpdate.run();
        String renderedHtml = compileTemplate(template);
        System.out.println("[Render] 组件渲染:\n" + renderedHtml);
        updated.run();
    }

    /**
     * 极简模板编译：支持 {{ expression }} 插值
     * 表达式可以是 data.key 或 props.key 或 methods 调用
     */
    protected String compileTemplate(String tmpl) {
        if (tmpl == null) return "";
        StringBuilder sb = new StringBuilder();
        int last = 0;
        int start;
        while ((start = tmpl.indexOf("{{", last)) != -1) {
            sb.append(tmpl, last, start);
            int end = tmpl.indexOf("}}", start + 2);
            if (end == -1) break;
            String expr = tmpl.substring(start + 2, end).trim();
            Object value = evaluateExpression(expr);
            sb.append(value != null ? value.toString() : "");
            last = end + 2;
        }
        sb.append(tmpl.substring(last));
        return sb.toString();
    }

    private Object evaluateExpression(String expr) {
        // 支持 data.xxx 或 props.xxx 或 方法名()
        if (expr.contains(".")) {
            String[] parts = expr.split("\\.");
            if (parts.length == 2) {
                String obj = parts[0];
                String key = parts[1];
                if ("data".equals(obj) && data != null) {
                    return data.get(key);
                } else if ("props".equals(obj) && props != null) {
                    return props.get(key);
                }
            }
        } else if (expr.endsWith("()")) {
            String methodName = expr.substring(0, expr.length() - 2);
            Function<Object[], Object> method = methods.get(methodName);
            if (method != null) {
                return method.apply(new Object[0]);
            }
        } else {
            // 先从 data 找，再从 props 找
            Object val = data.get(expr);
            if (val != null) return val;
            val = props.get(expr);
            if (val != null) return val;
        }
        return expr;
    }

    // 工具方法：更新 data
    public void setData(String key, Object value) {
        data.set(key, value);
    }

    public Object getData(String key) {
        return data.get(key);
    }

    // 获取样式字符串（用于最终输出）
    public String getStyleString() {
        return style != null ? style.toInlineStyle() : "";
    }

    // 模拟组件挂载
    public void mount(String selector) {
        beforeMount.run();
        System.out.println("组件挂载到 " + selector);
        render();
        mounted.run();
    }
}