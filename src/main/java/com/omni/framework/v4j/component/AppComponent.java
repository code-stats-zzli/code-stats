package com.omni.framework.v4j.component;

import java.util.Map;

public  class AppComponent extends Component {
    public AppComponent(Map<String, Object> props) {
        super(props);
    }

    @Override
    protected Map<String, Object> data() {
        return new java.util.HashMap<String, Object>() {{
            put("title", "SimpleVue Java 版");
            put("count", 0);
            put("user", new java.util.HashMap<String, Object>() {{
                put("name", "张三");
                put("age", 25);
            }});
        }};
    }

    @Override
    protected void initMethods() {
        methods.put("increment", args -> {
            int cur = (int) data.get("count");
            data.set("count", cur + 1);
            return null;
        });
        methods.put("decrement", args -> {
            int cur = (int) data.get("count");
            data.set("count", cur - 1);
            return null;
        });
    }

    @Override
    protected Style style() {
        return new Style()
                .set("color", "blue")
                .set("font-size", "20px");
    }

    {
        // 设置模板字符串
        template = """
            <div style="%s">
                <h1>{{ title }}</h1>
                <p>Count: {{ data.count }}</p>
                <p>User: {{ data.user.name }}, age {{ data.user.age }}</p>
                <button onclick="increment()">+</button>
                <button onclick="decrement()">-</button>
            </div>
        """.formatted(getStyleString());  // 注意：这里需要动态获取样式，实际应在 render 时读取
        // 为了动态样式，重写 render 方法会更准确，但这里简单演示
    }

    // 为了让模板中使用动态样式，重写 render
    @Override
    public void render() {
        String dynamicTemplate = """
            <div style="%s">
                <h1>{{ title }}</h1>
                <p>Count: {{ data.count }}</p>
                <p>User: {{ data.user.name }}, age {{ data.user.age }}</p>
                <button>+</button>
                <button>-</button>
            </div>
        """.formatted(getStyleString());
        // 重新编译模板
        String rendered = compileTemplate(dynamicTemplate);
        System.out.println("[Render] 组件渲染:\n" + rendered);
    }
}
