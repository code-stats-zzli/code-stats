/*
package com.omni.framework.scriptexec.executor.impl;



import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.BaseScriptExecutor;

import java.util.Map;

public class RestExecutor extends BaseScriptExecutor {

    private final RestTemplate restTemplate;

    public RestExecutor() {
        // 配置超时时间 30 秒
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);   // 连接超时 30s
        factory.setReadTimeout(30000);      // 读取超时 30s
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    protected String getDefaultLanguage() {
        return "rest";
    }

    @Override
    protected void preprocessParams(Map<String, Object> params) throws ScriptExecutionException {
        super.preprocessParams(params);
        params.put("executionMode", "source"); // 强制为 source
    }

    @Override
    protected ScriptSource resolveScriptSource(String scriptContent, Map<String, Object> params) throws Exception {
        ScriptSource source = new ScriptSource();
        source.setContent(scriptContent);
        source.setExecutionMode("source");
        source.setLanguage("rest");
        source.setCompiled(true); // 无需编译
        return source;
    }

    @Override
    protected void prepareExecution(ScriptSource source, Map<String, Object> params) throws Exception {
        // 无需准备
    }

    @Override
    protected String doExecute(ScriptSource source, Map<String, Object> params) throws Exception {
        String url = source.getContent();
        if (url == null || url.isEmpty()) {
            throw new ScriptExecutionException("URL is required for RestExecutor");
        }
        String methodStr = (String) params.getOrDefault("method", "GET");
        HttpMethod method = HttpMethod.valueOf(methodStr.toUpperCase());
        Map<String, String> headersMap = (Map<String, String>) params.get("headers");
        HttpHeaders headers = new HttpHeaders();
        if (headersMap != null) headersMap.forEach(headers::add);
        Object body = params.get("body");
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        return response.getBody();
    }
}*/
