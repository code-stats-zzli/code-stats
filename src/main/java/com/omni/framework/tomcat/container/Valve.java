package com.omni.framework.tomcat.container;

import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.connector.Response;

public interface Valve {
    void invoke(Request request, Response response, ValveChain chain) throws Exception;
    interface ValveChain {
        void invokeNext(Request request, Response response) throws Exception;
    }
}