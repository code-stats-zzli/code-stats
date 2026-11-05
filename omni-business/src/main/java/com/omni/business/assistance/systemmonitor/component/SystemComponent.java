package com.omni.business.assistance.systemmonitor.component;

import com.omni.business.assistance.systemmonitor.model.MonitorItemInfo;

/**
 * 所有监控组件必须实现的接口。
 * 引擎通过 getItemInfo() 获取任意时刻的组件快照。
 */
public interface SystemComponent {

    /** 组件唯一ID，如 "cpu" */
    String getId();

    /** 显示名称 */
    String getName();

    /** 分类：HARDWARE / OS / JAVA / RUNTIME */
    String getCategory();

    /** 获取该组件当前的信息快照（核心信息 + 运行时信息） */
    MonitorItemInfo getItemInfo();

    /**
     * 组件是否可用（例如无显卡、无权限读取时返回 false）
     */
    default boolean isAvailable() { return true; }

    /**
     * 采集频率提示（毫秒），供前端做轮询调度
     */
    default long getRefreshInterval() { return 1000L; }
}