package com.framework.tomcat.test;

import com.framework.log.LoggerFactory;
import com.framework.log.core.Logger;

public class Bootstrap {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);
    private Catalina catalina;

    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.init();
        bootstrap.load();
        bootstrap.start();
    }

    private void init() {
        catalina = new Catalina();
        log.info("Bootstrap: Catalina 实例创建完成");
    }

    private void load() {
        catalina.load();
        log.info("Bootstrap: load 完成");
    }

    private void start() {
        catalina.start();
        log.info("Bootstrap: start 完成，Tomcat 已启动");
    }
}