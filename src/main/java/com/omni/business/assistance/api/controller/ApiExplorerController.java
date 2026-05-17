package com.omni.business.assistance.api.controller;


import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.swag.annotation.ApiOperation;
import com.omni.framework.swag.core.ApiDefinition;
import com.omni.framework.util.JsonUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Controller
@RequestMapping("/apiexplorer/api")
public class ApiExplorerController {

    /**
     * 获取所有 API 定义（供前端 Swagger 风格界面使用）
     * GET /apiexplorer/api/apis
     */
    @RequestMapping("/apis")
    @ResponseBody
    public String getApiDefinitions() throws Exception {
        List<ApiDefinition> apis = new ArrayList<>();
        Map<String, Object> controllers = SpringContextHolder.getApplicationContext()
                .getBeanFactory().getBeansOfType(Object.class);

        for (Object controller : controllers.values()) {
            Class<?> clazz = controller.getClass();
            if (!clazz.isAnnotationPresent(Controller.class) &&
                    !clazz.isAnnotationPresent(RestController.class)) {
                continue;
            }

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
                api.setMethod("POST");  // 现有框架默认都支持 GET/POST，暂定 POST

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
                apis.add(api);
            }
        }
        return JsonUtil.toJson(apis);
    }
}