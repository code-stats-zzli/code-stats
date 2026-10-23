// 文件: src/main/java/com/omni/framework/spring/mvc/mapping/RequestMappingHandlerMapping.java
package com.omni.framework.spring.mvc.mapping;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.annotation.RestController;
import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;
import com.omni.framework.spring.mvc.support.HandlerExecutionChain;
import com.omni.framework.spring.mvc.support.HandlerMethod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestMappingHandlerMapping {
    private static final Logger log = LoggerFactory.getLogger(RequestMappingHandlerMapping.class);

    private final List<HandlerMethod> handlerMethods = new ArrayList<>();

    public void init(ConfigurableListableBeanFactory beanFactory) throws Exception {
        handlerMethods.clear();

        Map<String, Object> controllers = beanFactory.getBeansOfType(Object.class);
        for (Object controller : controllers.values()) {
            Class<?> clazz = controller.getClass();

            boolean isController = clazz.isAnnotationPresent(Controller.class)
                    || clazz.isAnnotationPresent(RestController.class);
            if (!isController) {
                continue;
            }

            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
                if (!basePath.startsWith("/")) {
                    basePath = "/" + basePath;
                }
            }

            boolean classResponseBody = clazz.isAnnotationPresent(RestController.class);

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }

                String methodPath = method.getAnnotation(RequestMapping.class).value();
                String fullPath = (basePath + methodPath).replaceAll("//", "/");
                if (!fullPath.startsWith("/")) {
                    fullPath = "/" + fullPath;
                }

                List<String> varNames = new ArrayList<>();
                String regex = pathToRegex(fullPath, varNames);

                boolean responseBody = classResponseBody
                        || method.isAnnotationPresent(ResponseBody.class);

                HandlerMethod handlerMethod = new HandlerMethod(
                        Pattern.compile(regex),
                        varNames,
                        controller,
                        method,
                        responseBody,
                        fullPath
                );

                handlerMethods.add(handlerMethod);
                log.debug("Mapped {} -> {}.{}",
                        fullPath,
                        controller.getClass().getSimpleName(),
                        method.getName());
            }
        }

        log.info("RequestMappingHandlerMapping 初始化完成，共映射 {} 个 HandlerMethod", handlerMethods.size());
    }

    public HandlerExecutionChain getHandler(String uri) {
        for (HandlerMethod handlerMethod : handlerMethods) {
            Matcher matcher = handlerMethod.getPattern().matcher(uri);
            if (!matcher.matches()) {
                continue;
            }

            Map<String, String> pathVars = new HashMap<>();
            for (int i = 0; i < handlerMethod.getVarNames().size(); i++) {
                pathVars.put(handlerMethod.getVarNames().get(i), matcher.group(i + 1));
            }
            return new HandlerExecutionChain(handlerMethod, pathVars);
        }
        return null;
    }

    private String pathToRegex(String path, List<String> varNames) {
        StringBuilder regex = new StringBuilder("^");
        String[] segments = path.split("/");
        for (String seg : segments) {
            if (seg.isEmpty()) {
                continue;
            }
            if (seg.startsWith("{") && seg.endsWith("}")) {
                varNames.add(seg.substring(1, seg.length() - 1));
                regex.append("/([^/]+)");
            } else {
                regex.append("/").append(seg);
            }
        }
        regex.append("/?$");
        return regex.toString();
    }
}