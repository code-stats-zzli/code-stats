package com.omni.framework.unzip.extractor;

import com.omni.framework.unzip.*;
import com.omni.framework.unzip.util.LanguageGuesser;
import com.omni.framework.spring.annotation.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

@Component
public class GzExtractor implements ArchiveExtractor {

    @Override public ArchiveType getType() { return ArchiveType.GZ; }

    @Override
    public boolean supports(byte[] head, String fileName) {
        if (ArchiveType.detectFromFileName(fileName) == ArchiveType.GZ) return true;
        return head != null && head.length >= 2
                && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B;
    }

    @Override
    public ExtractResult extract(ExtractRequest req) throws ExtractException, IOException {
        long start = System.currentTimeMillis();
        Path targetDir = req.getTargetDir();
        try { Files.createDirectories(targetDir); }
        catch (IOException e) { throw new ExtractException("创建目录失败", e); }

        String outName = req.getSourceFileName();
        if (outName == null || outName.isEmpty()) outName = "extracted.bin";
        else if (outName.toLowerCase().endsWith(".gz")) outName = outName.substring(0, outName.length() - 3);
        if (outName.isEmpty()) outName = "extracted.bin";

        Path resolved;
        try { resolved = PathTraversalGuard.resolveSafe(targetDir, outName); }
        catch (ExtractException e) { throw new ExtractException("输出文件名不合法: " + outName, e); }
        if (resolved.getParent() != null) Files.createDirectories(resolved.getParent());

        long written = 0;
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(req.getSourceBytes()));
             OutputStream out = Files.newOutputStream(resolved,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = gz.read(buf)) >= 0) {
                out.write(buf, 0, r);
                written += r;
                if (written > req.getMaxTotalBytes())
                    throw new ExtractException("解压后大小超过限制");
            }
        } catch (IOException e) {
            throw new ExtractException("GZ 解压失败: " + e.getMessage(), e);
        }

        ExtractResult result = ExtractResult.ok(ArchiveType.GZ, targetDir);
        ArchiveEntryInfo ei = new ArchiveEntryInfo();
        ei.setPath(outName);
        ei.setSize(written);
        ei.setLanguage(LanguageGuesser.guess(outName));
        ei.setBinary(LanguageGuesser.isBinary(outName));
        result.getEntries().add(ei);
        result.setTotalFiles(1);
        result.setTotalBytes(written);
        if (ei.isBinary()) result.setBinaryFiles(1);
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }
}