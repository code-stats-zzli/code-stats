package com.omni.framework.unzip.writer;

import com.omni.framework.spring.annotation.Component;
import com.omni.framework.unzip.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZipWriter implements ArchiveWriter {

    @Override public ArchiveType getType() { return ArchiveType.ZIP; }

    @Override
    public CompressResult compress(CompressRequest req) throws CompressException {
        long start = System.currentTimeMillis();
        long rawBytes = 0;
        int fileCount = 0;
        Charset cs = safeCharset(req.getEncoding());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, cs)) {
            for (CompressEntry e : req.getEntries()) {
                if (e.isDirectory()) {
                    String dn = e.getPath().endsWith("/") ? e.getPath() : e.getPath() + "/";
                    zos.putNextEntry(new ZipEntry(dn));
                    zos.closeEntry();
                    continue;
                }
                zos.putNextEntry(new ZipEntry(e.getPath()));
                if (e.getContent() != null) {
                    zos.write(e.getContent());
                    rawBytes += e.getContent().length;
                }
                zos.closeEntry();
                fileCount++;
            }
            zos.finish();
            byte[] data = baos.toByteArray();

            CompressResult r = CompressResult.ok(ArchiveType.ZIP);
            r.setData(data);
            r.setArchiveBytes(data.length);
            r.setRawBytes(rawBytes);
            r.setFileCount(fileCount);

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
            throw new CompressException("ZIP 压缩失败: " + ex.getMessage(), ex);
        }
    }

    private Charset safeCharset(String enc) {
        try { return Charset.forName(enc == null || enc.isEmpty() ? "UTF-8" : enc); }
        catch (Exception e) { return Charset.forName("UTF-8"); }
    }
}