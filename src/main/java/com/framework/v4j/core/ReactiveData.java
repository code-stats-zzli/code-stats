package com.framework.v4j.core;

import java.util.HashMap;
import java.util.Map;

public class ReactiveData {
    private final Map<String, Object> data = new HashMap<>();
    private final Map<String, Dep> depMap = new HashMap<>();

    public ReactiveData(Map<String, Object> init) {
        if (init != null) {
            for (Map.Entry<String, Object> e : init.entrySet()) {
                set(e.getKey(), e.getValue());
            }
        }
    }

    public Object get(String key) {
        Dep dep = depMap.computeIfAbsent(key, k -> new Dep());
        dep.depend();
        Object val = data.get(key);
        if (val instanceof Map && !(val instanceof ReactiveData)) {
            val = Reactive.toReactive(val);
            data.put(key, val);
        }
        return val;
    }

    public void set(String key, Object value) {
        Object old = data.get(key);
        Object reactiveVal = Reactive.toReactive(value);
        data.put(key, reactiveVal);
        if (old != reactiveVal && (old == null || !old.equals(reactiveVal))) {
            Dep dep = depMap.get(key);
            if (dep != null) dep.notifyObservers();
        }
    }

    public Map<String, Object> raw() {
        return data;
    }
}