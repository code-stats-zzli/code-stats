package com.codestats.framework.tomcat.test;

import com.codestats.framework.tomcat.util.ServletMapping;

/**
 * 核心控制器，负责解析配置（模拟 server.xml）、创建并启动 Connector。
 */
public class Catalina {

    private Connector connector;

    public void load() {
        // 模拟解析 server.xml，这里硬编码配置：端口 8080，Servlet 映射
        int port = 8080;
        // 注册 Servlet 映射：路径 /hello 对应 HelloServlet
        ServletMapping.addMapping("/hello", "com.demo.tomcat.HelloServlet");
        System.out.println("Catalina: 加载配置，端口=" + port);
        connector = new Connector(port);
        System.out.println("Catalina: Connector 创建完成");
    }

    public void start() {
        connector.start();
        System.out.println("Catalina: Connector 启动成功");
    }
}