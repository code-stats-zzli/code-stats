package com.omni.framework.tomcat.session;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SessionManager {
    private static final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong();

    public static HttpSession getSession(String id) {
        return sessions.get(id);
    }

    public static HttpSession createSession() {
        String id = Long.toHexString(idGenerator.incrementAndGet());
        HttpSessionImpl session = new HttpSessionImpl(id);
        sessions.put(id, session);
        return session;
    }

    public static void invalidate(String id) {
        sessions.remove(id);
    }

    private static class HttpSessionImpl implements HttpSession {
        private final String id;
        private final long creationTime;
        private long lastAccessedTime;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private boolean valid = true;

        public HttpSessionImpl(String id) {
            this.id = id;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessedTime = creationTime;
        }

        @Override public String getId() { return id; }
        @Override public long getCreationTime() { return creationTime; }
        @Override public long getLastAccessedTime() { return lastAccessedTime; }
        @Override public void setAttribute(String name, Object value) { attributes.put(name, value); }
        @Override public Object getAttribute(String name) { return attributes.get(name); }
        @Override public void removeAttribute(String name) { attributes.remove(name); }
        @Override public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }
        @Override public void invalidate() { valid = false; SessionManager.invalidate(id); }
        @Override public boolean isNew() { return lastAccessedTime == creationTime; }
        public void access() { lastAccessedTime = System.currentTimeMillis(); }
    }
}