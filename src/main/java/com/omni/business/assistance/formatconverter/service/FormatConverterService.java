package com.omni.business.assistance.formatconverter.service;

import com.omni.framework.converter.ConvertException;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.converter.Converter;
import com.omni.framework.converter.ConverterRegistry;
import com.omni.framework.converter.FormatDetector;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.util.Arrays;

/**
 * 格式转换业务服务。
 */
@Service
public class FormatConverterService {
    private static final Logger log = LoggerFactory.getLogger(FormatConverterService.class);

    @Autowired
    private ConverterRegistry registry;

    private final long maxSize = Long.parseLong(
            ConfigLoader.getProperty("converter.max-file-size", "104857600"));

    public ConvertResult convert(ConvertRequest req) {
        // 源格式自动探测
        if (req.getSourceType() == null && req.getSourceFileName() != null) {
            byte[] head = req.getSourceBytes() != null && req.getSourceBytes().length >= 4
                    ? Arrays.copyOf(req.getSourceBytes(), 4) : req.getSourceBytes();
            req.setSourceType(FormatDetector.detect(req.getSourceFileName(), head));
        }
        if (req.getSourceType() == null || req.getTargetType() == null) {
            return ConvertResult.error("未指定源格式或目标格式");
        }
        if (req.getSourceType() == req.getTargetType()) {
            return ConvertResult.error("源格式与目标格式相同");
        }

        long size = req.getSourceBytes() != null ? req.getSourceBytes().length
                : (req.getSourceText() != null ? req.getSourceText().length() : 0);
        if (size > maxSize) {
            return ConvertResult.error("文件大小超过 " + (maxSize / 1024 / 1024) + " MB");
        }

        Converter converter = registry.resolve(req.getSourceType(), req.getTargetType());
        if (converter == null) {
            return ConvertResult.error(String.format("不支持的转换：%s → %s",
                    req.getSourceType().ext, req.getTargetType().ext));
        }

        try {
            return converter.convert(req);
        } catch (ConvertException e) {
            return ConvertResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("转换异常: {}→{}", req.getSourceType(), req.getTargetType(), e);
            return ConvertResult.error("转换失败: " + e.getMessage());
        }
    }

    public ConverterRegistry getRegistry() { return registry; }
}