package com.omni.framework.unzip.writer;

import com.omni.framework.spring.annotation.Component;
import com.omni.framework.unzip.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** POSIX ustar Tar 压缩器（纯 JDK 实现） */
@Component
public class TarWriter implements ArchiveWriter {

    @Override public ArchiveType getType() { return ArchiveType.TAR; }

    @Override
    public CompressResult compress(CompressRequest req) throws CompressException {
        long start = System.currentTimeMillis();
        long rawBytes = 0;
        int fileCount = 0;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (CompressEntry e : req.getEntries()) {
                if (e.isDirectory()) {
                    writeHeader(baos, e.getPath() + "/", 0, '5');
                } else {
                    byte[] content = e.getContent() != null ? e.getContent() : new byte[0];
                    writeHeader(baos, e.getPath(), content.length, '0');
                    baos.write(content);
                    int padding = (512 - (content.length % 512)) % 512;
                    for (int i = 0; i < padding; i++) baos.write(0);
                    rawBytes += content.length;
                    fileCount++;
                }
            }
            // 结束块：两个 512 全零块
            for (int i = 0; i < 1024; i++) baos.write(0);

            byte[] data = baos.toByteArray();
            CompressResult r = CompressResult.ok(ArchiveType.TAR);
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
            throw new CompressException("TAR 压缩失败: " + ex.getMessage(), ex);
        }
    }

    private void writeHeader(OutputStream out, String name, long size, char typeflag) throws IOException {
        byte[] h = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        if (nameBytes.length > 100) throw new IOException("文件名过长（>100 字符）: " + name);
        System.arraycopy(nameBytes, 0, h, 0, nameBytes.length);
        writeOctal(h, 100, 8, 0644);
        writeOctal(h, 108, 8, 0);
        writeOctal(h, 116, 8, 0);
        writeOctal(h, 124, 12, size);
        writeOctal(h, 136, 12, System.currentTimeMillis() / 1000);
        for (int i = 148; i < 156; i++) h[i] = ' ';
        h[156] = (byte) typeflag;
        h[257] = 'u'; h[258] = 's'; h[259] = 't';
        h[260] = 'a'; h[261] = 'r'; h[262] = 0;
        h[263] = '0'; h[264] = '0';

        long sum = 0;
        for (byte b : h) sum += (b & 0xFF);
        String sumStr = Long.toOctalString(sum);
        while (sumStr.length() < 6) sumStr = "0" + sumStr;
        for (int i = 0; i < 6; i++) h[148 + i] = (byte) sumStr.charAt(i);
        h[154] = 0;
        h[155] = ' ';
        out.write(h);
    }

    private void writeOctal(byte[] buf, int off, int len, long value) {
        String s = Long.toOctalString(value);
        if (s.length() > len - 1) s = s.substring(s.length() - (len - 1));
        int pad = len - 1 - s.length();
        for (int i = 0; i < pad; i++) buf[off + i] = '0';
        for (int i = 0; i < s.length(); i++) buf[off + pad + i] = (byte) s.charAt(i);
        buf[off + len - 1] = 0;
    }
}