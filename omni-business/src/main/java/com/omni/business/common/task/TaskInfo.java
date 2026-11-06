package com.omni.business.common.task;

import java.util.Map;

/**
 * 通用任务信息接口。
 * <p>由 {@link TaskRegistry} 管理的 TaskContext 实现。</p>
 */
public interface TaskInfo {

    String getTaskId();

    long getStartTime();

    long getEndTime();

    String getState();

    String getErrorMessage();

    /** 任务特有字段（由各 TaskContext 自行组装，TaskRegistry 不再关心） */
    Map<String, Object> toStatusMap();

    /** 是否已终止（COMPLETED / ERROR / CANCELLED / FAILED 视为终止） */
    default boolean isTerminated() {
        String s = getState();
        return "COMPLETED".equals(s) || "ERROR".equals(s)
                || "CANCELLED".equals(s) || "FAILED".equals(s);
    }
}