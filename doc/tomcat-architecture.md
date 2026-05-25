``markdown
# 自研嵌入式 Tomcat 架构设计

## 1. 整体架构

遵循经典 Tomcat 容器层次模型：
Server
└─ Service
├─ Connector
└─ Engine
└─ Host
└─ Context
└─ Wrapper (Servlet)
- **Connector**: 监听端口，解析 HTTP 请求，提交给线程池处理。
- **Pipeline-Valve**: 责任链模式，每个容器都有自己的 Pipeline，Valve 依次处理请求。
- **Wrapper**: 封装 Servlet 实例，负责调用 `service()` 方法。

## 2. 请求处理流程

1. `Connector` 接收 Socket 连接，解析生成 `Request` 和 `Response` 对象。
2. 调用 `Engine` 的 `Pipeline` 的第一个 Valve。
3. Valve 根据请求 URI 找到对应的 `Host` → `Context` → `Wrapper`。
4. 若配置了 Filter，则 `ApplicationFilterChain` 依次执行。
5. 最终调用 `Servlet.service()`，将响应写回客户端。

## 3. 类加载隔离

`WebappClassLoader` 继承 `URLClassLoader`，负责加载 `/WEB-INF/classes` 和 `/WEB-INF/lib` 下的类，与父 ClassLoader 隔离，实现不同 Web 应用之间类互不干扰。

## 4. 关键代码位置

- `Catalina`: 启动入口，组装容器层次。
- `Connector`: `com.omni.framework.tomcat.connector.Connector`
- `StandardContext`: `com.omni.framework.tomcat.container.StandardContext`
- `SimplePipeline`: `com.omni.framework.tomcat.container.SimplePipeline`

## 5. 配置方式

通过 `application.properties` 配置：

```properties
server.port=28080
context.path=/omni
servlet.class=com.omni.framework.spring.mvc.DispatcherServlet