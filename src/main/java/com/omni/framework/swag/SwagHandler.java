package com.omni.framework.swag;

import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.swag.annotation.ApiModule;
import com.omni.framework.swag.annotation.ApiOperation;
import com.omni.framework.swag.core.ApiDefinition;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Swagger 风格 API 元数据处理器
 * 负责扫描所有 Controller，提取 API 定义并按模块分组
 */
public class SwagHandler {

    /**
     * 模块化的 API 结果
     */
    public static class ModuleApiResult {
        private String moduleName;
        private String moduleDescription;
        private List<ApiDefinition> apis = new ArrayList<>();

        public String getModuleName() { return moduleName; }
        public void setModuleName(String moduleName) { this.moduleName = moduleName; }
        public String getModuleDescription() { return moduleDescription; }
        public void setModuleDescription(String moduleDescription) { this.moduleDescription = moduleDescription; }
        public List<ApiDefinition> getApis() { return apis; }
        public void setApis(List<ApiDefinition> apis) { this.apis = apis; }
    }

    /**
     * 获取所有 API 定义（按模块分组）
     */
    public static List<ModuleApiResult> getModuleApis() throws Exception {
        Map<String, ModuleApiResult> moduleMap = new LinkedHashMap<>();
        ConfigurableListableBeanFactory beanFactory = SpringContextHolder.getApplicationContext().getBeanFactory();
        Map<String, Object> controllers = beanFactory.getBeansOfType(Object.class);

        for (Object controller : controllers.values()) {
            Class<?> clazz = controller.getClass();
            if (!clazz.isAnnotationPresent(Controller.class) &&
                    !clazz.isAnnotationPresent(RestController.class)) {
                continue;
            }

            // 获取模块信息
            String moduleName = "未分组";
            String moduleDesc;
            ApiModule apiModule = clazz.getAnnotation(ApiModule.class);
            if (apiModule != null) {
                moduleName = apiModule.value();
                moduleDesc = apiModule.description();
            } else {
                moduleDesc = "";
                // 根据包名自动推断模块
                String pkg = clazz.getPackage().getName();
                if (pkg.contains(".agent")) moduleName = "AI Agent";
                else if (pkg.contains(".codestat")) moduleName = "代码分析";
                else if (pkg.contains(".directory")) moduleName = "目录管理";
                else if (pkg.contains(".table.mysql")) moduleName = "数据库管理";
                else if (pkg.contains(".introduction")) moduleName = "项目介绍";
                else if (pkg.contains(".apiexplorer")) moduleName = "API 调试";
                else moduleName = clazz.getSimpleName().replace("Controller", "");
            }

            String finalModuleName = moduleName;
            ModuleApiResult moduleResult = moduleMap.computeIfAbsent(moduleName, k -> {
                ModuleApiResult r = new ModuleApiResult();
                r.setModuleName(finalModuleName);
                r.setModuleDescription(moduleDesc);
                return r;
            });

            String basePath = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
                if (!basePath.startsWith("/")) basePath = "/" + basePath;
            }

            for (Method method : clazz.getDeclaredMethods()) {
                RequestMapping rm = method.getAnnotation(RequestMapping.class);
                if (rm == null) continue;

                String methodPath = rm.value();
                String fullPath = (basePath + methodPath).replaceAll("//", "/");
                if (!fullPath.startsWith("/")) fullPath = "/" + fullPath;

                ApiDefinition api = new ApiDefinition();
                api.setId(UUID.randomUUID().toString().substring(0, 8));
                api.setPath(fullPath);
                api.setMethod(detectMethod(method));

                // 读取 @ApiOperation 注解
                ApiOperation apiOp = method.getAnnotation(ApiOperation.class);
                if (apiOp != null) {
                    api.setSummary(apiOp.value());
                    api.setDescription(apiOp.notes());
                } else {
                    api.setSummary(method.getName());
                    api.setDescription("无描述");
                }

                // 解析参数
                boolean hasRequestBody = false;
                for (Parameter param : method.getParameters()) {
                    RequestParam rp = param.getAnnotation(RequestParam.class);
                    PathVariable pv = param.getAnnotation(PathVariable.class);
                    RequestBody rb = param.getAnnotation(RequestBody.class);

                    if (rb != null) {
                        hasRequestBody = true;
                        continue;
                    }

                    if (rp != null) {
                        ApiDefinition.ApiParameter p = new ApiDefinition.ApiParameter();
                        p.setName(rp.value().isEmpty() ? param.getName() : rp.value());
                        p.setIn("query");
                        p.setRequired(rp.required());
                        p.setType(param.getType().getSimpleName());
                        p.setDescription("");
                        api.getParameters().add(p);
                    } else if (pv != null) {
                        ApiDefinition.ApiParameter p = new ApiDefinition.ApiParameter();
                        p.setName(pv.value().isEmpty() ? param.getName() : pv.value());
                        p.setIn("path");
                        p.setRequired(pv.required());
                        p.setType(param.getType().getSimpleName());
                        p.setDescription("");
                        api.getParameters().add(p);
                    }
                }
                api.setRequestBody(hasRequestBody);

                // 推断响应 Content-Type 和示例
                inferResponseInfo(method, api);

                moduleResult.getApis().add(api);
            }
        }

        return new ArrayList<>(moduleMap.values());
    }

    /**
     * 检测 HTTP 方法（简单规则：方法名包含 get/post/delete/put）
     */
    private static String detectMethod(Method method) {
        String name = method.getName().toLowerCase();
        if (name.startsWith("get")) return "GET";
        if (name.startsWith("post")) return "POST";
        if (name.startsWith("delete")) return "DELETE";
        if (name.startsWith("put")) return "PUT";
        return "POST";
    }

    /**
     * 推断响应信息：Content-Type 和示例
     */
    private static void inferResponseInfo(Method method, ApiDefinition api) {
        Class<?> returnType = method.getReturnType();
        String contentType = "application/json";
        String example = "{}";

        // 检查是否有 @ResponseBody 注解
        boolean hasResponseBody = method.isAnnotationPresent(ResponseBody.class);
        if (!hasResponseBody && returnType == String.class) {
            contentType = "text/html";
            example = "<html>...</html>";
        } else if (returnType == String.class) {
            contentType = "text/plain";
            example = "\"示例字符串\"";
        } else if (returnType == void.class) {
            contentType = "无返回体";
            example = "";
        } else if (returnType == int.class || returnType == Integer.class) {
            contentType = "application/json";
            example = "123";
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            contentType = "application/json";
            example = "true";
        } else {
            // 复杂对象，尝试生成简单示例
            contentType = "application/json";
            example = "{\n  \"field\": \"value\"\n}";
        }

        api.setResponseContentType(contentType);
        api.setResponseExample(example);
    }
}