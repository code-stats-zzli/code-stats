package com.omni.framework.unzip.extractor;

import com.omni.framework.unzip.*;
import com.omni.framework.unzip.util.LanguageGuesser;
import com.omni.framework.spring.annotation.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class TarExtractor implements ArchiveExtractor {

    @Override public ArchiveType getType() { return ArchiveType.TAR; }

    @Override
    public boolean supports(byte[] head, String fileName) {
        if (ArchiveType.detectFromFileName(fileName) == ArchiveType.TAR) return true;
        if (head != null && head.length > 262) {
            String magic = new String(head, 257, 5, StandardCharsets.US_ASCII);
            return magic.startsWith("ustar");
        }
        return false;
    }

    @Override
    public ExtractResult extract(ExtractRequest req) throws ExtractException {
        try (InputStream in = new ByteArrayInputStream(req.getSourceBytes())) {
            return extractFromStream(in, req);
        } catch (IOException e) {
            throw new ExtractException("TAR 解压失败: " + e.getMessage(), e);
        }
    }

    protected ExtractResult extractFromStream(InputStream in, ExtractRequest req) throws ExtractException {
        long start = System.currentTimeMillis();
        Path targetDir = req.getTargetDir();
        try { Files.createDirectories(targetDir); }
        catch (IOException e) { throw new ExtractException("创建目录失败", e); }

        ExtractResult result = ExtractResult.ok(ArchiveType.TAR, targetDir);

        byte[] header = new byte[512];
        String pendingLongName = null;

        try {
            while (true) {
                int n = readFully(in, header);
                if (n < 512 || isAllZero(header)) break;

                String name = readAscii(header, 0, 100);
                long size = readOctal(header, 124, 12);
                char typeflag = (char) (header[156] & 0xFF);
                String prefix = readAscii(header, 345, 155);
                String magic = readAscii(header, 257, 6);

                String fullName = name;
                if (!prefix.isEmpty() && magic.startsWith("ustar")) {
                    fullName = prefix + "/" + name;
                }

                long dataBlocks = (size + 511) / 512;
                long padding = dataBlocks * 512 - size;

                if (typeflag == 'L') {
                    byte[] nb = new byte[(int) Math.min(size, 65535)];
                    int r = readFully(in, nb);
                    if (r > 0) pendingLongName = new String(nb, 0, r, StandardCharsets.UTF_8).trim();
                    skipFully(in, size - nb.length + padding);
                    continue;
                }

                if (typeflag == '0' || typeflag == 0 || typeflag == '5') {
                    if (pendingLongName != null) {
                        fullName = pendingLongName;
                        pendingLongName = null;
                    }
                    Path resolved;
                    try { resolved = PathTraversalGuard.resolveSafe(targetDir, fullName); }
                    catch (ExtractException e) { skipFully(in, dataBlocks * 512); continue; }

                    boolean isDir = (typeflag == '5') || fullName.endsWith("/");
                    if (isDir) {
                        Files.createDirectories(resolved);
                        ArchiveEntryInfo ei = dir(fullName);
                        result.getEntries().add(ei);
                        result.setTotalDirs(result.getTotalDirs() + 1);
                        skipFully(in, dataBlocks * 512);
                    } else {
                        Path parent = resolved.getParent();
                        if (parent != null) Files.createDirectories(parent);
                        if (!req.isOverwrite() && Files.exists(resolved)) {
                            skipFully(in, dataBlocks * 512);
                            continue;
                        }
                        long written = 0;
                        try (OutputStream out = Files.newOutputStream(resolved,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            byte[] buf = new byte[8192];
                            long remaining = size;
                            while (remaining > 0) {
                                int toRead = (int) Math.min(buf.length, remaining);
                                int r = in.read(buf, 0, toRead);
                                if (r < 0) break;
                                out.write(buf, 0, r);
                                remaining -= r;
                                written += r;
                            }
                        }
                        result.setTotalBytes(result.getTotalBytes() + written);
                        if (result.getTotalBytes() > req.getMaxTotalBytes())
                            throw new ExtractException("解压后大小超过限制");
                        ArchiveEntryInfo ei = file(fullName, written);
                        result.getEntries().add(ei);
                        result.setTotalFiles(result.getTotalFiles() + 1);
                        if (ei.isBinary()) result.setBinaryFiles(result.getBinaryFiles() + 1);
                        skipFully(in, padding);
                    }
                    if (result.getEntries().size() >= req.getMaxFiles())
                        throw new ExtractException("文件数量超过限制");
                    continue;
                }

                skipFully(in, dataBlocks * 512);
            }
        } catch (IOException e) {
            throw new ExtractException("TAR 解析失败: " + e.getMessage(), e);
        }
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
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

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int r = in.read(buf, total, buf.length - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }
    private static void skipFully(InputStream in, long n) throws IOException {
        long skipped = 0;
        while (skipped < n) {
            long s = in.skip(n - skipped);
            if (s <= 0) { if (in.read() < 0) break; s = 1; }
            skipped += s;
        }
    }
    private static String readAscii(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0) end++;
        return new String(buf, off, end - off, StandardCharsets.US_ASCII).trim();
    }
    private static long readOctal(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0 && buf[end] != ' ') end++;
        String s = new String(buf, off, end - off, StandardCharsets.US_ASCII).trim();
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s, 8); } catch (Exception e) { return 0; }
    }
    private static boolean isAllZero(byte[] buf) {
        for (byte b : buf) if (b != 0) return false;
        return true;
    }
}