package com.omni.business.assistance.systemcheck.command;

import com.omni.business.assistance.systemcheck.model.Finding;
import java.util.List;

/** 系统检查命令接口（命令模式） */
public interface SystemCheckCommand {
    /** 唯一ID（与配置文件 key 一致） */
    String getId();
    /** 显示名称 */
    String getName();
    /** 执行检查并返回结果列表 */
    List<Finding> execute(SystemCheckContext context) throws Exception;
}