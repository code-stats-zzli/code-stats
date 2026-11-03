package com.omni.framework.converter;

import java.util.Set;

/**
 * 格式转换策略接口。
 * <p>每种转换能力实现一个类并加 {@code @Component}，注册中心自动收集。</p>
 * <p>新增转换能力 = 新增实现类，不修改 Service / Controller。</p>
 */
public interface Converter {

    /** 转换器唯一名称，用于日志与前端展示 */
    String getName();

    /** 能接收的源格式集合 */
    Set<FormatType> supportedSources();

    /** 能输出的目标格式集合 */
    Set<FormatType> supportedTargets();

    /** 优先级：数值越大越优先（同源格式多引擎竞争时使用） */
    default int getPriority() { return 100; }

    /** 执行转换 */
    ConvertResult convert(ConvertRequest request) throws ConvertException;

    /** 是否支持 source → target */
    default boolean supports(FormatType source, FormatType target) {
        return supportedSources().contains(source) && supportedTargets().contains(target);
    }
}