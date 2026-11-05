package com.omni.business.assistance.sys.service.portscan;

/**
 * 服务识别策略。
 * 输入端口号 / 协议 / 进程名，输出服务名与分类。
 * 新增规则 = 新增一个实现类并替换 serviceIdentifier 即可。
 */
public interface ServiceIdentifier {
    /** 返回识别出的服务名（无法识别时返回 "未知"） */
    String identify(int port, String protocol, String processName);

    /** 返回服务分类（数据库 / Web / 系统服务 / 缓存 / 中间件 / 容器 / 搜索 / 其他） */
    String category(int port);
}