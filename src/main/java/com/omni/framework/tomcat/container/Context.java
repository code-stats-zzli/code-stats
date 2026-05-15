package com.omni.framework.tomcat.container;

import com.omni.framework.tomcat.filter.Filter;

public interface Context extends Container {
    String getDocBase();
    void setDocBase(String docBase);
    void addServletMapping(String pattern, String servletName);
    String findServletMapping(String uri);
    void addFilter(Filter filter, String filterName);
    Filter findFilter(String filterName);
    String[] findFilters();
}