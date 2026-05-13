package com.framework.tomcat.test;

/**
 * 引导类，通过反射调用 Catalina，实现启动逻辑与容器的解耦。
 * 模拟真实 Tomcat 的 Bootstrap 类。
 */
public class Bootstrap {

    private Catalina catalina;

    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.init();
        bootstrap.load();
        bootstrap.start();
    }

    private void init() {
        // 直接实例化 Catalina（真实 Tomcat 使用反射，这里简化）
        catalina = new Catalina();
        System.out.println("Bootstrap: Catalina 实例创建完成");
    }

    private void load() {
        catalina.load();
        System.out.println("Bootstrap: load 完成");
    }

    private void start() {
        catalina.start();
        System.out.println("Bootstrap: start 完成，Tomcat 已启动");
    }
}