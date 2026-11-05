package com.omni.framework.unzip;

import java.nio.charset.StandardCharsets;

public final class ArchiveTypeDetector {
    private ArchiveTypeDetector() {}

    public static ArchiveType detect(String fileName, byte[] head) {
        ArchiveType t = ArchiveType.detectFromFileName(fileName);
        if (t != null) return t;

        if (head != null && head.length >= 4) {
            if (head[0] == 'P' && head[1] == 'K'
                    && (head[2] == 3 || head[2] == 5 || head[2] == 7)) {
                return ArchiveType.ZIP;
            }
            if ((head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
                return ArchiveType.GZ;
            }
            if (head.length >= 262) {
                String magic = new String(head, 257, 5, StandardCharsets.US_ASCII);
                if (magic.startsWith("ustar")) return ArchiveType.TAR;
            }
        }
        return null;
    }
}