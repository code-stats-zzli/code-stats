package com.codestats.framework.tomcat.filter;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;
import com.codestats.framework.tomcat.servlet.Servlet;
import java.util.ArrayList;
import java.util.List;

public class ApplicationFilterChain implements FilterChain {
    private final List<Filter> filters = new ArrayList<>();
    private int index = 0;
    private Servlet servlet;

    public void addFilter(Filter filter) { filters.add(filter); }
    public void setServlet(Servlet servlet) { this.servlet = servlet; }

    @Override
    public void doFilter(Request request, Response response) throws Exception {
        if (index < filters.size()) {
            filters.get(index++).doFilter(request, response, this);
        } else if (servlet != null) {
            servlet.service(request, response);
        }
    }
}