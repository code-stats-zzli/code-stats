package com.codestats.framework.tomcat.container;

import com.codestats.framework.tomcat.connector.Request;
import com.codestats.framework.tomcat.connector.Response;

import java.util.ArrayList;
import java.util.List;

public class SimplePipeline implements Pipeline {
    private final List<Valve> valves = new ArrayList<>();
    private Valve basic;
    private final Container container;

    public SimplePipeline(Container container) { this.container = container; }

    @Override public Valve getBasic() { return basic; }
    @Override public void setBasic(Valve valve) { this.basic = valve; }
    @Override public void addValve(Valve valve) { valves.add(valve); }
    @Override public Valve[] getValves() { return valves.toArray(new Valve[0]); }
    @Override public void removeValve(Valve valve) { valves.remove(valve); }

    @Override
    public void invoke(Request request, Response response) throws Exception {
        new ValveChainImpl().invokeNext(request, response);
    }

    private class ValveChainImpl implements Valve.ValveChain {
        private int index = 0;

        @Override
        public void invokeNext(Request request, Response response) throws Exception {
            if (index < valves.size()) {
                valves.get(index++).invoke(request, response, this);
            } else if (basic != null) {
                basic.invoke(request, response, this);
            } else {
                // 默认处理：交给容器或子容器
                if (container instanceof Wrapper) {
                    ((StandardWrapper) container).invokeServlet(request, response);
                } else if (container instanceof Context) {
                    String path = request.getUri();
                    String servletName = ((Context) container).findServletMapping(path);
                    if (servletName != null) {
                        Container wrapper = container.findChild(servletName);
                        if (wrapper != null) wrapper.getPipeline().invoke(request, response);
                        else fallback404(response);
                    } else fallback404(response);
                } else if (container instanceof Host) {
                    String contextPath = request.getContextPath();
                    Container context = container.findChild(contextPath);
                    if (context != null) context.getPipeline().invoke(request, response);
                    else fallback404(response);
                } else if (container instanceof Engine) {
                    Container host = container.findChild(((Engine) container).getDefaultHost());
                    if (host != null) host.getPipeline().invoke(request, response);
                    else fallback404(response);
                }
            }
        }

        private void fallback404(Response response) throws Exception {
            response.setStatus(404, "Not Found");
            response.write("<h1>404 - Not Found</h1>");
            response.send();
        }
    }
}