package com.codestats.framework.tomcat;

import com.codestats.framework.spring.config.ConfigLoader;
import com.codestats.framework.tomcat.connector.Connector;
import com.codestats.framework.tomcat.container.*;
import com.codestats.framework.tomcat.listener.ContextListenerDemo;
import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;

public class Catalina {
    private static final Logger log = LoggerFactory.getLogger(Catalina.class);
    private Server server;

    public void load() {
        StandardServer server = new StandardServer();
        StandardService service = new StandardService();
        service.setName("Catalina");
        server.addService(service);

        int port = Integer.parseInt(ConfigLoader.getProperty("server.port", "28080"));
        Connector connector = new Connector(port);
        service.setConnector(connector);
        connector.setService(service);

        StandardEngine engine = new StandardEngine();
        engine.setName("Engine");
        engine.setDefaultHost("localhost");
        service.setEngine(engine);

        StandardHost host = new StandardHost();
        host.setName("localhost");
        host.setAppBase("webapps");
        engine.addChild(host);

        String contextPath = ConfigLoader.getProperty("context.path", "/codestats");
        StandardContext context = new StandardContext();
        context.setName(contextPath);
        context.setDocBase("webapps/demo");
        context.setParentClassLoader(Thread.currentThread().getContextClassLoader());
        host.addChild(context);
        context.addLifecycleListener(new ContextListenerDemo());

        String servletClassName = ConfigLoader.getProperty("servlet.class",
                "com.framework.spring.mvc.DispatcherServlet");
        StandardWrapper wrapper = new StandardWrapper();
        wrapper.setName("dispatcher");
        wrapper.setServletClass(servletClassName);
        context.addChild(wrapper);
        context.addServletMapping("/*", wrapper.getName());

        this.server = server;
    }

    public void start() {
        server.init();
        server.start();
    }

    public void stop() {
        server.stop();
        server.destroy();
    }
}