package com.framework.tomcat;


import com.framework.spring.config.ConfigLoader;
import com.framework.tomcat.connector.Connector;

import com.framework.tomcat.container.*;
import com.framework.tomcat.listener.ContextListenerDemo;

public class Catalina {
    private Server server;

    public void load() {
        // 1. Server
        StandardServer server = new StandardServer();

        // 2. Service
        StandardService service = new StandardService();
        service.setName("Catalina");
        server.addService(service);

        // 3. Connector（从配置文件读取端口）
        int port = Integer.parseInt(ConfigLoader.getProperty("server.port", "28080"));
        Connector connector = new Connector(port);
        service.setConnector(connector);
        connector.setService(service);

        // 4. Engine
        StandardEngine engine = new StandardEngine();
        engine.setName("Engine");
        engine.setDefaultHost("localhost");
        service.setEngine(engine);

        // 5. Host
        StandardHost host = new StandardHost();
        host.setName("localhost");
        host.setAppBase("webapps");
        engine.addChild(host);

        // 6. Context
        StandardContext context = new StandardContext();
        context.setName("/demo");
        context.setDocBase("webapps/demo");
        context.setParentClassLoader(Thread.currentThread().getContextClassLoader());
        host.addChild(context);
        context.addLifecycleListener(new ContextListenerDemo());

        // 7. Wrapper (DispatcherServlet) - 从配置读取类名
        String servletClassName = ConfigLoader.getProperty("servlet.class",
                "com.framework.spring.mvc.DispatcherServlet");  // 提供默认值
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