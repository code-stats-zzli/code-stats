package com.codestats;

import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;
import com.codestats.framework.tomcat.Catalina;

public class Bootstrap {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    public static void main(String[] args) {
        Catalina catalina = new Catalina();
        catalina.load();
        catalina.start();
        log.info("Tomcat ready. Access http://localhost:8080/codestats/demo/index.html");
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {
            log.error("Main thread interrupted", e);
        }
    }
}