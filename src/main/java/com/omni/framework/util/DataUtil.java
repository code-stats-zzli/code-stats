package com.omni.framework.util;

import java.util.HashMap;
import java.util.Map;

public class DataUtil {

    public static <K, V> Map<K, V> toMap(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("参数必须为偶数个");
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }
}
