package com.omni.business.assistance.sys.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSH 密码内存缓存。
 *
 * <p><b>目的</b>：密码不入库、不回显，但需要在页面会话期间持续可用。
 * 按 connectionId 绑定密码，页面关闭时主动清理，兜底 2 小时滑动过期。</p>
 *
 * <p><b>安全</b>：不写日志明文、不写磁盘、过期立即移除。</p>
 */
@Component
public class SshCredentialCache {
    private static final Logger log = LoggerFactory.getLogger(SshCredentialCache.class);

    /** 滑动过期时间：2 小时 */
    private static final long TTL_MS = 2 * 60 * 60 * 1000L;

    private static class Entry {
        final String password;
        volatile long lastAccess;
        Entry(String password) {
            this.password = password;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public SshCredentialCache() {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ssh-credential-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictExpired, 5, 5, TimeUnit.MINUTES);
    }

    /** 保存或刷新密码 */
    public void put(String connectionId, String password) {
        if (connectionId == null || password == null || password.isEmpty()) return;
        cache.put(connectionId, new Entry(password));
    }

    /** 取密码并刷新 lastAccess；不存在返回 null */
    public String get(String connectionId) {
        if (connectionId == null) return null;
        Entry e = cache.get(connectionId);
        if (e == null) return null;
        e.lastAccess = System.currentTimeMillis();
        return e.password;
    }

    public boolean contains(String connectionId) {
        return connectionId != null && cache.containsKey(connectionId);
    }

    /** 页面关闭时调用 */
    public void clear(String connectionId) {
        if (connectionId == null) return;
        cache.remove(connectionId);
        log.info("SSH 密码缓存已清理: connectionId={}", connectionId);
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> me = it.next();
            if (now - me.getValue().lastAccess > TTL_MS) {
                it.remove();
                log.info("SSH 密码缓存过期清理: connectionId={}", me.getKey());
            }
        }
    }
}