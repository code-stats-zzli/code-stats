package com.omni.business.assistance.systemcheck.command;

import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.util.List;
import java.util.Map;

/** 系统检查命令接口（命令模式） */
public interface SystemCheckCommand {
    /** 唯一ID（与配置文件 key 一致） */
    String getId();
    /** 显示名称 */
    String getName();
    /** 执行检查并返回结果列表 */
    List<Finding> execute(SystemCheckContext context) throws Exception;

    /**
     * 模块详细配置字段定义（前端渲染用）
     * 返回 null 或空列表表示该模块无额外配置
     * 每个字段: {key, label, type(list|number|string), value, [hint]}
     */
    default List<Map<String, Object>> getConfigFields(CheckConfig config) {
        return null;
    }

    /** 预估执行耗时（秒），用于前端显示"预计需要 X 秒" */
    default int getEstimatedSeconds(CheckConfig config) {
        return 5;
    }
}