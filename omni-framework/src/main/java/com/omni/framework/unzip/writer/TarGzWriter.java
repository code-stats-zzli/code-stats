package com.omni.framework.unzip.writer;

import com.omni.framework.spring.annotation.Component;
import com.omni.framework.unzip.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

@Component
public class TarGzWriter extends TarWriter {

    @Override public ArchiveType getType() { return ArchiveType.TAR_GZ; }

    @Override
    public CompressResult compress(CompressRequest req) throws CompressException {
        CompressRequest tarReq = new CompressRequest();
        tarReq.setTargetType(ArchiveType.TAR);
        tarReq.setEntries(req.getEntries());
        tarReq.setEncoding(req.getEncoding());
        tarReq.setOutputFile(null);
        CompressResult tarResult = super.compress(tarReq);
        if (tarResult == null || !tarResult.isSuccess()) return tarResult;

        long start = System.currentTimeMillis();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(tarResult.getData());
            gz.finish();
            byte[] gzData = baos.toByteArray();

            CompressResult r = CompressResult.ok(ArchiveType.TAR_GZ);
            r.setData(gzData);
            r.setArchiveBytes(gzData.length);
            r.setRawBytes(tarResult.getRawBytes());
            r.setFileCount(tarResult.getFileCount());

            if (req.getOutputFile() != null) {
                Path parent = req.getOutputFile().getParent();
                if (parent != null) Files.createDirectories(parent);
                try (OutputStream out = Files.newOutputStream(req.getOutputFile(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    out.write(gzData);
                }
                r.setOutputFile(req.getOutputFile());
            }
            r.setDurationMs(System.currentTimeMillis() - start);
            return r;
        } catch (IOException ex) {
            throw new CompressException("TAR.GZ 压缩失败: " + ex.getMessage(), ex);
        }
    }
}