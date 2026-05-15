package com.codestats.framework.tomcat.container;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;

public interface Pipeline {
    Valve getBasic();
    void setBasic(Valve valve);
    void addValve(Valve valve);
    Valve[] getValves();
    void removeValve(Valve valve);
    void invoke(Request request, Response response) throws Exception;
}