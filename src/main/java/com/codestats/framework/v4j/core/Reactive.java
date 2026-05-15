package com.codestats.framework.v4j.core;

import java.util.Map;

public class Reactive {
    public static ReactiveData observe(Map<String, Object> data) {
        return new ReactiveData(data);
    }

    @SuppressWarnings("unchecked")
    public static Object toReactive(Object value) {
        if (value instanceof Map && !(value instanceof ReactiveData)) {
            return observe((Map<String, Object>) value);
        }
        return value;
    }
}