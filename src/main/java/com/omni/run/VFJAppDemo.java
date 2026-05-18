/*
package com.omni.run;



import com.omni.framework.v4j.V4J;
import com.omni.framework.v4j.component.Component;
import com.omni.framework.v4j.component.Style;

import java.util.HashMap;
import java.util.Map;

public class VFJAppDemo {
    public static void main(String[] args) {
        // 注册组件
        V4J.component("my-counter", CounterComponent.class);
        V4J.component("user-card", UserCardComponent.class);

        // 创建根组件并挂载
        Component root = V4J.createComponent("my-counter", null);
        root.mount("#app");
    }
}

class CounterComponent extends Component {
    public CounterComponent(Map<String, Object> props) {
        super(props);
        // 可以在构造后修改样式，或者通过 style() 方法
    }

    @Override
    protected Map<String, Object> data() {
        Map<String, Object> d = new HashMap<>();
        d.put("count", 10);
        return d;
    }

    @Override
    protected void initMethods() {
        methods.put("add", args -> {
            int c = (int) data.get("count");
            data.set("count", c + 1);
            return null;
        });
        methods.put("sub", args -> {
            int c = (int) data.get("count");
            data.set("count", c - 1);
            return null;
        });
    }

    @Override
    protected Style style() {
        return new Style().set("font-family", "sans-serif").set("margin", "20px");
    }

    {
        template = "";*/
/*"
            <div class="counter" style="%s">
                <h2>计数: {{ data.count }}</h2>
                <button @click="add">增加</button>
                <button @click="sub">减少</button>
            </div>
        """;*//*

    }

    @Override
    public void render() {
        String filled = String.format(template, getStyleString());
        String compiled = compileTemplate(filled);
        System.out.println(compiled);
    }
}

class UserCardComponent extends Component {
    public UserCardComponent(Map<String, Object> props) {
        super(props);
    }

    @Override
    protected Map<String, Object> data() {
        Map<String, Object> d = new HashMap<>();
        d.put("userName", "李四");
        d.put("email", "lisi@example.com");
        return d;
    }

    @Override
    protected Style style() {
        return new Style().set("border", "1px solid #ccc").set("padding", "10px");
    }

    {
        template = """
            <div style="%s">
                <strong>{{ data.userName }}</strong> ({{ data.email }})
            </div>
        """;
    }
}*/
