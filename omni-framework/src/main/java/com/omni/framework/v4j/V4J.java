package com.omni.framework.v4j;



import com.omni.framework.v4j.component.AppComponent;
import com.omni.framework.v4j.component.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局 Vue 实例，负责组件注册和挂载
 */
public class V4J {
    private static final Map<String, Class<? extends Component>> components = new ConcurrentHashMap<>();

    public static void component(String name, Class<? extends Component> clazz) {
        components.put(name, clazz);
    }

    public static Component createComponent(String name, Map<String, Object> props) {
        Class<? extends Component> clazz = components.get(name);
        if (clazz == null) throw new RuntimeException("Component not found: " + name);
        try {
            return clazz.getDeclaredConstructor(Map.class).newInstance(props);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create component", e);
        }
    }

    // 主演示方法
    public static void main(String[] args) {
        // 注册组件（见下方 App 组件）
        component("app", AppComponent.class);
        Component app = createComponent("app", null);
        app.mount("#app");
    }
}
