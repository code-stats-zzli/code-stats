package com.framework.tomcat.container;

import com.framework.tomcat.connector.Request;
import com.framework.tomcat.connector.Response;

public interface Valve {
    void invoke(Request request, Response response, ValveChain chain) throws Exception;
    interface ValveChain {
        void invokeNext(Request request, Response response) throws Exception;
    }
}