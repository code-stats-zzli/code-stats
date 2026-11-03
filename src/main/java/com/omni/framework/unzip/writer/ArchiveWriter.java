package com.omni.framework.unzip.writer;

import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.CompressException;
import com.omni.framework.unzip.CompressRequest;
import com.omni.framework.unzip.CompressResult;

/**
 * 压缩格式 SPI。
 * 新增格式 = 新增一个 @Component 实现类，registry 自动发现。
 */
public interface ArchiveWriter {
    ArchiveType getType();
    CompressResult compress(CompressRequest request) throws CompressException;
    default int getPriority() { return 100; }
}