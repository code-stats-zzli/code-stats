package com.omni.framework.unzip.extractor;

import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.ExtractException;
import com.omni.framework.unzip.ExtractRequest;
import com.omni.framework.unzip.ExtractResult;
import com.omni.framework.spring.annotation.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

@Component
public class TarGzExtractor extends TarExtractor {

    @Override public ArchiveType getType() { return ArchiveType.TAR_GZ; }

    @Override
    public boolean supports(byte[] head, String fileName) {
        return ArchiveType.detectFromFileName(fileName) == ArchiveType.TAR_GZ;
    }

    @Override
    public ExtractResult extract(ExtractRequest req) throws ExtractException {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(req.getSourceBytes()))) {
            return extractFromStream(gz, req);
        } catch (IOException e) {
            throw new ExtractException("TAR.GZ 解压失败: " + e.getMessage(), e);
        }
    }
}