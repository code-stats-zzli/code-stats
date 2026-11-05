package com.omni.framework.unzip.extractor;

import com.omni.framework.unzip.*;
import com.omni.framework.unzip.util.LanguageGuesser;
import com.omni.framework.spring.annotation.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ZipExtractor implements ArchiveExtractor {

    @Override public ArchiveType getType() { return ArchiveType.ZIP; }

    @Override
    public boolean supports(byte[] head, String fileName) {
        if (ArchiveType.detectFromFileName(fileName) == ArchiveType.ZIP) return true;
        return head != null && head.length >= 2 && head[0] == 'P' && head[1] == 'K';
    }

    @Override
    public ExtractResult extract(ExtractRequest req) throws ExtractException {
        long start = System.currentTimeMillis();
        Path targetDir = req.getTargetDir();
        try { Files.createDirectories(targetDir); }
        catch (IOException e) { throw new ExtractException("创建目录失败: " + targetDir, e); }

        ExtractResult result = ExtractResult.ok(ArchiveType.ZIP, targetDir);
        Charset cs = safeCharset(req.getEncoding());

        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(req.getSourceBytes()), cs)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (result.getEntries().size() >= req.getMaxFiles())
                    throw new ExtractException("文件数量超过限制: " + req.getMaxFiles());

                String name = entry.getName();
                Path resolved;
                try { resolved = PathTraversalGuard.resolveSafe(targetDir, name); }
                catch (ExtractException e) { zis.closeEntry(); continue; }

                if (entry.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(resolved);
                    ArchiveEntryInfo ei = dir(name);
                    result.getEntries().add(ei);
                    result.setTotalDirs(result.getTotalDirs() + 1);
                } else {
                    Path parent = resolved.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    if (!req.isOverwrite() && Files.exists(resolved)) { zis.closeEntry(); continue; }

                    long written = Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                    result.setTotalBytes(result.getTotalBytes() + written);
                    if (result.getTotalBytes() > req.getMaxTotalBytes())
                        throw new ExtractException("解压后大小超过限制");
                    ArchiveEntryInfo ei = file(name, written);
                    result.getEntries().add(ei);
                    result.setTotalFiles(result.getTotalFiles() + 1);
                    if (ei.isBinary()) result.setBinaryFiles(result.getBinaryFiles() + 1);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new ExtractException("ZIP 解压失败: " + e.getMessage(), e);
        }
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }

    protected Charset safeCharset(String enc) {
        try { return Charset.forName(enc == null || enc.isEmpty() ? "UTF-8" : enc); }
        catch (Exception e) { return Charset.forName("UTF-8"); }
    }

    protected ArchiveEntryInfo dir(String name) {
        ArchiveEntryInfo ei = new ArchiveEntryInfo();
        ei.setPath(name);
        ei.setDirectory(true);
        ei.setSize(0);
        ei.setLanguage("directory");
        return ei;
    }

    protected ArchiveEntryInfo file(String name, long size) {
        ArchiveEntryInfo ei = new ArchiveEntryInfo();
        ei.setPath(name);
        ei.setSize(size);
        ei.setLanguage(LanguageGuesser.guess(name));
        ei.setBinary(LanguageGuesser.isBinary(name));
        return ei;
    }
}