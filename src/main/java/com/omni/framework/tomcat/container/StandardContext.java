package com.omni.framework.tomcat.container;

import com.omni.framework.tomcat.core.Lifecycle;
import com.omni.framework.tomcat.core.LifecycleState;
import com.omni.framework.tomcat.filter.Filter;
import com.omni.framework.tomcat.loader.WebappClassLoader;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class StandardContext extends ContainerBase implements Context {
    private String docBase;
    private ClassLoader parentLoader;
    private WebappClassLoader webappLoader;
    private final Map<String, String> servletMappings = new HashMap<>();
    private final Map<String, Filter> filters = new HashMap<>();
    private String defaultServletName;  // 新增：存储 /* 映射的 servlet 名

    @Override public String getDocBase() { return docBase; }
    @Override public void setDocBase(String docBase) { this.docBase = docBase; }

    public void setParentClassLoader(ClassLoader parent) { this.parentLoader = parent; }

    @Override
    public void init() {
        state = LifecycleState.INITIALIZING;
        fireLifecycleEvent(Lifecycle.INIT_EVENT, null);
        try {
            URL classesUrl = new File(docBase + "/WEB-INF/classes/").toURI().toURL();
            URL libUrl = new File(docBase + "/WEB-INF/lib/").toURI().toURL();
            webappLoader = new WebappClassLoader(parentLoader, new URL[]{classesUrl, libUrl});
        } catch (Exception e) { webappLoader = new WebappClassLoader(parentLoader, new URL[0]); }
        for (Container child : findChildren()) child.init();
        state = LifecycleState.INITIALIZED;
    }

    @Override
    public void start() {
        state = LifecycleState.STARTING;
        fireLifecycleEvent(Lifecycle.START_EVENT, null);
        for (Container child : findChildren()) child.start();
        state = LifecycleState.STARTED;
    }

    @Override
    public void stop() {
        state = LifecycleState.STOPPING;
        fireLifecycleEvent(Lifecycle.STOP_EVENT, null);
        for (Container child : findChildren()) child.stop();
        state = LifecycleState.STOPPED;
    }

    @Override
    public void destroy() {
        state = LifecycleState.DESTROYING;
        fireLifecycleEvent(Lifecycle.DESTROY_EVENT, null);
        for (Container child : findChildren()) child.destroy();
        if (webappLoader != null) try { webappLoader.close(); } catch (Exception e) {}
        state = LifecycleState.DESTROYED;
    }

    @Override
    public void addServletMapping(String pattern, String servletName) {
        servletMappings.put(pattern, servletName);
        if ("/*".equals(pattern)) {
            this.defaultServletName = servletName;
        }
    }

    @Override
    public String findServletMapping(String uri) {
        // 1. 精确匹配
        String servletName = servletMappings.get(uri);
        if (servletName != null) {
            return servletName;
        }
        // 2. 匹配 /*（默认 Servlet）
        if (defaultServletName != null) {
            return defaultServletName;
        }
        return null;
    }

    @Override public void addFilter(Filter filter, String filterName) { filters.put(filterName, filter); }
    @Override public Filter findFilter(String filterName) { return filters.get(filterName); }
    @Override public String[] findFilters() { return filters.keySet().toArray(new String[0]); }
    @Override public ClassLoader getLoader() { return webappLoader; }
}