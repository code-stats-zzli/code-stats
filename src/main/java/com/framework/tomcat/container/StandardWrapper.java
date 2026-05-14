package com.framework.tomcat.container;

import com.framework.tomcat.core.Lifecycle;
import com.framework.tomcat.core.LifecycleState;
import com.framework.tomcat.filter.ApplicationFilterChain;
import com.framework.tomcat.filter.Filter;
import com.framework.tomcat.servlet.Servlet;
import com.framework.tomcat.connector.Request;
import com.framework.tomcat.connector.Response;
import com.framework.log.LoggerFactory;
import com.framework.log.core.Logger;

public class StandardWrapper extends ContainerBase implements Wrapper {
    private static final Logger log = LoggerFactory.getLogger(StandardWrapper.class);
    private String servletClass;
    private Servlet servletInstance;

    @Override public String getServletClass() { return servletClass; }
    @Override public void setServletClass(String servletClass) { this.servletClass = servletClass; }

    @Override
    public void init() {
        state = LifecycleState.INITIALIZING;
        fireLifecycleEvent(Lifecycle.INIT_EVENT, null);
        try {
            ClassLoader loader = getLoader();
            Class<?> clazz = loader.loadClass(servletClass);
            servletInstance = (Servlet) clazz.getDeclaredConstructor().newInstance();
            servletInstance.init();
        } catch (Exception e) {
            log.error("Failed to initialize servlet: {}", servletClass, e);
        }
        state = LifecycleState.INITIALIZED;
    }

    @Override
    public void start() { state = LifecycleState.STARTING; fireLifecycleEvent(Lifecycle.START_EVENT, null); state = LifecycleState.STARTED; }
    @Override
    public void stop() { state = LifecycleState.STOPPING; fireLifecycleEvent(Lifecycle.STOP_EVENT, null);
        if (servletInstance != null) servletInstance.destroy();
        state = LifecycleState.STOPPED; }
    @Override
    public void destroy() { state = LifecycleState.DESTROYING; fireLifecycleEvent(Lifecycle.DESTROY_EVENT, null); state = LifecycleState.DESTROYED; }

    public void invokeServlet(Request request, Response response) throws Exception {
        Context context = (Context) getParent();
        ApplicationFilterChain chain = new ApplicationFilterChain();
        for (String filterName : context.findFilters()) {
            Filter filter = context.findFilter(filterName);
            if (filter != null) chain.addFilter(filter);
        }
        chain.setServlet(servletInstance);
        chain.doFilter(request, response);
    }
}