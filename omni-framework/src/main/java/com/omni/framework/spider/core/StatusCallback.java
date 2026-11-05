package com.omni.framework.spider.core;

import com.omni.framework.spider.model.Article;

/**
 * 爬虫状态回调接口，用于实时通知任务执行状态
 * 由调用方（如 SpiderTaskService）实现
 */
public interface StatusCallback {
    /**
     * 更新任务状态
     * @param taskId 任务ID
     * @param currentStep 当前步骤描述
     * @param currentUrl 正在处理的URL
     * @param newArticle 新获取的文章（可为null）
     */
    void updateStatus(String taskId, String currentStep, String currentUrl, Article newArticle);
}