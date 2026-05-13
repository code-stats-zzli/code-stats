package com.framework.tomcat.connector;

import com.framework.tomcat.container.Engine;
import com.framework.tomcat.container.Service;
import com.framework.tomcat.core.Lifecycle;
import com.framework.tomcat.core.LifecycleListener;
import com.framework.tomcat.core.LifecycleState;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Connector implements Lifecycle {
    private int port;
    private Service service;
    private ExecutorService threadPool;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public Connector(int port) { this.port = port; }

    public void setService(Service service) { this.service = service; }

    @Override
    public void init() { threadPool = Executors.newCachedThreadPool(); }

    @Override
    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Connector listening on " + port);
                while (running) {
                    Socket socket = serverSocket.accept();
                    threadPool.submit(() -> process(socket));
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void process(Socket socket) {
        try (java.io.InputStream in = socket.getInputStream(); java.io.OutputStream out = socket.getOutputStream()) {
            Request request = new Request(in);
            request.parse();
            Response response = new Response(out);
            Engine engine = service.getEngine();
            if (engine != null) {
                engine.getPipeline().invoke(request, response);
            } else {
                response.setStatus(500, "Internal Server Error");
                response.write("<h1>No Engine</h1>");
                response.send();
            }
        } catch (Exception e) { e.printStackTrace(); }
        finally { try { socket.close(); } catch (IOException e) {} }
    }

    @Override
    public void stop() { running = false; try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {} }
    @Override
    public void destroy() { stop(); if (threadPool != null) threadPool.shutdown(); }
    @Override public void addLifecycleListener(LifecycleListener listener) {}
    @Override public LifecycleListener[] findLifecycleListeners() { return new LifecycleListener[0]; }
    @Override public void removeLifecycleListener(LifecycleListener listener) {}
    @Override public LifecycleState getState() { return null; }
}