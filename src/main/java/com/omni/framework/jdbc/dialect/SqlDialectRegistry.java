package com.omni.framework.jdbc.dialect;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 方言注册中心：懒加载所有 SqlDialect 实现。
 */
@Component
public class SqlDialectRegistry {
    private static final Logger log = LoggerFactory.getLogger(SqlDialectRegistry.class);

    private volatile Map<String, SqlDialect> dialects;

    public SqlDialect get(String name) {
        if (name == null) return null;
        return getDialects().get(name.toLowerCase());
    }

    public SqlDialect getOrMysql(String name) {
        SqlDialect d = get(name);
        return d != null ? d : get("mysql");
    }

    private Map<String, SqlDialect> getDialects() {
        if (dialects != null) return dialects;
        synchronized (this) {
            if (dialects != null) return dialects;
            Map<String, SqlDialect> map = new HashMap<>();
            try {
                Map<String, SqlDialect> beans = SpringContextHolder.getApplicationContext()
                        .getBeanFactory().getBeansOfType(SqlDialect.class);
                for (SqlDialect d : beans.values()) {
                    map.put(d.getName().toLowerCase(), d);
                    log.info("注册 SQL 方言: {}", d.getName());
                }
            } catch (Exception e) {
                log.warn("收集 SqlDialect 失败", e);
            }
            dialects = Collections.unmodifiableMap(map);
            return dialects;
        }
    }
}