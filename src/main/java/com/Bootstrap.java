package com;

import com.framework.tomcat.Catalina;

public class Bootstrap {
    public static void main(String[] args) {
        Catalina catalina = new Catalina();
        catalina.load();
        catalina.start();
        System.out.println("Tomcat ready. Access http://localhost:8080/codestats/face/index.html");
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) { }
    }
}