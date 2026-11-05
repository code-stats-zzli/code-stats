package com.omni.framework.unzip.writer;

import com.omni.framework.spring.annotation.Component;
import com.omni.framework.unzip.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** 单文件 gzip（.gz 只能压一个文件） */
@Component
public class GzWriter implements ArchiveWriter {

    @Override public ArchiveType getType() { return ArchiveType.GZ; }

    @Override
    public CompressResult compress(CompressRequest req) throws CompressException {
        long start = System.currentTimeMillis();
        List<CompressEntry> files = new ArrayList<>();
        for (CompressEntry e : req.getEntries()) if (!e.isDirectory()) files.add(e);
        if (files.size() != 1) {
            throw new CompressException("GZ 格式仅支持单文件压缩，当前 " + files.size()
                    + " 个文件，请改用 zip / tar / tar.gz");
        }
        CompressEntry entry = files.get(0);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            if (entry.getContent() != null) gz.write(entry.getContent());
            gz.finish();
            byte[] data = baos.toByteArray();

            CompressResult r = CompressResult.ok(ArchiveType.GZ);
            r.setData(data);
            r.setArchiveBytes(data.length);
            r.setRawBytes(entry.getContent() != null ? entry.getContent().length : 0);
            r.setFileCount(1);

            if (req.getOutputFile() != null) {
                Path parent = req.getOutputFile().getParent();
                if (parent != null) Files.createDirectories(parent);
                try (OutputStream out = Files.newOutputStream(req.getOutputFile(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    out.write(data);
                }
                r.setOutputFile(req.getOutputFile());
            }
            r.setDurationMs(System.currentTimeMillis() - start);
            return r;
        } catch (IOException ex) {
            throw new CompressException("GZ 压缩失败: " + ex.getMessage(), ex);
        }
    }
}