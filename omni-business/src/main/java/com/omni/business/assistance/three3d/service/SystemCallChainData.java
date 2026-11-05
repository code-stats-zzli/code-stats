package com.omni.business.assistance.three3d.service;

import java.util.*;

/**
 * 内置系统全链路数据（从 Tomcat 到 JDBC 的 30 个核心类）
 * 作为默认的调用链展示数据，不依赖项目分析。
 */
public class SystemCallChainData {

    /**
     * 获取固定的节点和边数据
     * @return Map 包含 nodes 和 links
     */
    public static Map<String, Object> getFixedData() {
        List<Map<String, String>> nodes = new ArrayList<>();
        List<Map<String, String>> links = new ArrayList<>();

        // ---------- 定义 30 个核心类（节点） ----------
        String[] classNames = {
                "com.omni.Bootstrap",
                "com.omni.framework.spring.boot.SpringApplication",
                "com.omni.framework.tomcat.Catalina",
                "com.omni.framework.tomcat.connector.Connector",
                "com.omni.framework.tomcat.connector.Request",
                "com.omni.framework.tomcat.connector.Response",
                "com.omni.framework.tomcat.container.StandardEngine",
                "com.omni.framework.tomcat.container.StandardHost",
                "com.omni.framework.tomcat.container.StandardContext",
                "com.omni.framework.tomcat.container.StandardWrapper",
                "com.omni.framework.tomcat.container.SimplePipeline",
                "com.omni.framework.spring.mvc.DispatcherServlet",
                "com.omni.framework.spring.mvc.HandlerMapping",
                "com.omni.framework.spring.context.support.AnnotationConfigApplicationContext",
                "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory",
                "com.omni.framework.spring.beans.factory.support.BeanDefinition",
                "com.omni.framework.spring.annotation.Controller",
                "com.omni.framework.spring.annotation.Service",
                "com.omni.framework.spring.annotation.Repository",
                "com.omni.framework.spring.annotation.Mapper",
                "com.omni.framework.jdbc.JdbcTemplate",
                "com.omni.framework.jdbc.DataSourceUtils",
                "com.omni.framework.pool.SimpleDataSource",
                "com.omni.framework.pool.PooledConnection",
                "com.omni.framework.mybatis.proxy.MapperProxy",
                "com.omni.framework.mybatis.annotation.Select",
                "com.omni.framework.log.LoggerFactory",
                "com.omni.framework.log.core.Logger",
                "com.omni.framework.log.appender.ConsoleAppender",
                "com.omni.framework.tomcat.servlet.HttpServlet"
        };

        // 类型映射：根据包名推断类型
        for (String className : classNames) {
            String type = "class";
            if (className.contains(".controller")) type = "controller";
            else if (className.contains(".service")) type = "service";
            else if (className.contains(".mapper") || className.contains(".mybatis")) type = "mapper";
            else if (className.contains(".tomcat") || className.contains(".connector")) type = "controller";
            Map<String, String> node = new HashMap<>();
            node.put("id", className);
            node.put("type", type);
            node.put("label", className.substring(className.lastIndexOf('.') + 1));
            node.put("size", "2");
            nodes.add(node);
        }

        // ---------- 定义边（依赖关系） ----------
        addLink(links, "com.omni.Bootstrap", "com.omni.framework.spring.boot.SpringApplication");
        addLink(links, "com.omni.framework.spring.boot.SpringApplication", "com.omni.framework.tomcat.Catalina");
        addLink(links, "com.omni.framework.tomcat.Catalina", "com.omni.framework.tomcat.connector.Connector");
        addLink(links, "com.omni.framework.tomcat.connector.Connector", "com.omni.framework.tomcat.connector.Request");
        addLink(links, "com.omni.framework.tomcat.connector.Connector", "com.omni.framework.tomcat.connector.Response");
        addLink(links, "com.omni.framework.tomcat.connector.Connector", "com.omni.framework.tomcat.container.SimplePipeline");
        addLink(links, "com.omni.framework.tomcat.container.SimplePipeline", "com.omni.framework.tomcat.container.StandardEngine");
        addLink(links, "com.omni.framework.tomcat.container.StandardEngine", "com.omni.framework.tomcat.container.StandardHost");
        addLink(links, "com.omni.framework.tomcat.container.StandardHost", "com.omni.framework.tomcat.container.StandardContext");
        addLink(links, "com.omni.framework.tomcat.container.StandardContext", "com.omni.framework.tomcat.container.StandardWrapper");
        addLink(links, "com.omni.framework.tomcat.container.StandardWrapper", "com.omni.framework.spring.mvc.DispatcherServlet");
        addLink(links, "com.omni.framework.spring.mvc.DispatcherServlet", "com.omni.framework.spring.mvc.HandlerMapping");
        addLink(links, "com.omni.framework.spring.mvc.DispatcherServlet", "com.omni.framework.spring.context.support.AnnotationConfigApplicationContext");
        addLink(links, "com.omni.framework.spring.context.support.AnnotationConfigApplicationContext", "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory");
        addLink(links, "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory", "com.omni.framework.spring.beans.factory.support.BeanDefinition");
        addLink(links, "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory", "com.omni.framework.spring.annotation.Controller");
        addLink(links, "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory", "com.omni.framework.spring.annotation.Service");
        addLink(links, "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory", "com.omni.framework.spring.annotation.Repository");
        addLink(links, "com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory", "com.omni.framework.spring.annotation.Mapper");
        addLink(links, "com.omni.framework.spring.mvc.DispatcherServlet", "com.omni.framework.jdbc.JdbcTemplate");
        addLink(links, "com.omni.framework.jdbc.JdbcTemplate", "com.omni.framework.jdbc.DataSourceUtils");
        addLink(links, "com.omni.framework.jdbc.DataSourceUtils", "com.omni.framework.pool.SimpleDataSource");
        addLink(links, "com.omni.framework.pool.SimpleDataSource", "com.omni.framework.pool.PooledConnection");
        addLink(links, "com.omni.framework.spring.annotation.Mapper", "com.omni.framework.mybatis.proxy.MapperProxy");
        addLink(links, "com.omni.framework.mybatis.proxy.MapperProxy", "com.omni.framework.mybatis.annotation.Select");
        addLink(links, "com.omni.framework.spring.boot.SpringApplication", "com.omni.framework.log.LoggerFactory");
        addLink(links, "com.omni.framework.log.LoggerFactory", "com.omni.framework.log.core.Logger");
        addLink(links, "com.omni.framework.log.core.Logger", "com.omni.framework.log.appender.ConsoleAppender");
        addLink(links, "com.omni.framework.tomcat.container.StandardWrapper", "com.omni.framework.tomcat.servlet.HttpServlet");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("links", links);
        return result;
    }

    private static void addLink(List<Map<String, String>> links, String source, String target) {
        Map<String, String> link = new HashMap<>();
        link.put("source", source);
        link.put("target", target);
        links.add(link);
    }
}