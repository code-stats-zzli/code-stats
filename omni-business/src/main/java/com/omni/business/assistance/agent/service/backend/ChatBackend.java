package com.omni.business.assistance.agent.service.backend;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天后端策略接口。
 * 每种后端实现一个类，通过 {@code @Component} 自动注册到 {@link ChatBackendRegistry}，
 * 新增后端只需新增实现类，无需修改 {@code AIChatService}。
 */
public interface ChatBackend {

    /** 后端唯一名称，如 "ollama" / "deepseek" / "custom" */
    String getName();

    /**
     * 执行对话请求。
     * @param model       模型名称
     * @param messages    消息历史
     * @param temperature 温度
     * @param customUrl   自定义 URL（仅 custom 后端使用，其他可忽略）
     * @param customKey   自定义 API Key（同上）
     * @return 回答文本
     */
    String chat(String model,
                List<Map<String, String>> messages,
                double temperature,
                String customUrl,
                String customKey) throws Exception;
}