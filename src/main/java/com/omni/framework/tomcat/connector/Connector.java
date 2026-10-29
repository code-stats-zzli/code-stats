package com.omni.framework.tomcat.connector;

import com.omni.framework.tomcat.container.Engine;
import com.omni.framework.tomcat.container.Service;
import com.omni.framework.tomcat.core.Lifecycle;
import com.omni.framework.tomcat.core.LifecycleListener;
import com.omni.framework.tomcat.core.LifecycleState;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Connector implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(Connector.class);
    private int port;
    private Service service;
    private ExecutorService threadPool;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread listenerThread;   // ★ 新增：保留监听线程引用

    public Connector(int port) { this.port = port; }

    public void setService(Service service) { this.service = service; }

    @Override
    public void init() {
        // ★ 修复2：所有工作线程设为守护线程，JVM 可以正常退出
        threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tomcat-connector-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start() {
        running = true;
        // ★ 修复2：监听线程也设为守护线程，避免阻塞 JVM 退出（原先非守护线程需手动 stop 才能退出）
        listenerThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                log.info("Connector listening on {}", port);
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        threadPool.submit(() -> process(socket));
                    } catch (SocketException e) {
                        if (running) {
                            log.error("Socket error while running", e);
                        } else {
                            log.info("Connector stopped, accept interrupted.");
                        }
                        break;
                    } catch (IOException e) {
                        log.error("Accept failed", e);
                    }
                }
            } catch (IOException e) {
                log.error("Connector error", e);
            }
        }, "tomcat-connector-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void process(Socket socket) {
        long startTime = System.currentTimeMillis();
        // ====== 连接建立日志 ======
        log.debug("TCP 连接建立: remote={}, local={}",
                socket.getRemoteSocketAddress(), socket.getLocalSocketAddress());

        try (java.io.InputStream in = socket.getInputStream();
             java.io.OutputStream out = socket.getOutputStream()) {
            Request request = new Request(in);
            request.setSocket(socket);
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
            log.debug("TCP 连接处理完成: remote={}, method={}, uri={}, 耗时={}ms",
                    socket.getRemoteSocketAddress(),
                    request.getMethod(), request.getUri(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Request processing failed, remote={}", socket.getRemoteSocketAddress(), e);
        } finally {
            try { socket.close(); } catch (IOException e) { log.warn("Close socket error", e); }
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Close server socket error", e);
        }
    }

    @Override
    public void destroy() {
        stop();
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Thread pool did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override public void addLifecycleListener(LifecycleListener listener) {}
    @Override public LifecycleListener[] findLifecycleListeners() { return new LifecycleListener[0]; }
    @Override public void removeLifecycleListener(LifecycleListener listener) {}
    @Override public LifecycleState getState() { return null; }
}