package com.framework.tomcat.test;

import com.framework.tomcat.util.ServletMapping;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 连接器，负责监听指定端口，接收 HTTP 请求并分发给对应 Servlet。
 */
public class Connector {

    private final int port;
    private final ExecutorService threadPool;
    private volatile boolean running;

    public Connector(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void start() {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Connector: 启动监听端口 " + port);
            while (running) {
                Socket socket = serverSocket.accept();
                threadPool.submit(() -> process(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void process(Socket socket) {
        try (InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {

            // 1. 解析请求
            Request request = new Request(input);
            request.parse();

            // 2. 创建响应对象
            Response response = new Response(output);

            // 3. 根据 URI 找到对应的 Servlet
            String uri = request.getUri();
            String servletClassName = ServletMapping.getServletClass(uri);
            if (servletClassName != null) {
                // 通过反射实例化 Servlet 并调用 service 方法
                Class<?> servletClass = Class.forName(servletClassName);
                Servlet servlet = (Servlet) servletClass.getDeclaredConstructor().newInstance();
                servlet.service(request, response);
            } else {
                // 未找到映射，返回 404
                response.setStatus(404, "Not Found");
                response.write("<h1>404 - Not Found</h1>");
            }

            // 4. 发送响应
            response.send();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
        threadPool.shutdown();
    }
}