package com.omni.framework.unzip;

public enum ArchiveType {
    ZIP("zip"), TAR("tar"), TAR_GZ("tar.gz"), GZ("gz");

    public final String ext;
    ArchiveType(String ext) { this.ext = ext; }

    public static ArchiveType ofExt(String ext) {
        if (ext == null) return null;
        String e = ext.toLowerCase().replaceFirst("^\\.", "");
        for (ArchiveType t : values()) if (t.ext.equals(e)) return t;
        return null;
    }

    public static ArchiveType detectFromFileName(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return TAR_GZ;
        int dot = lower.lastIndexOf('.');
        if (dot > 0) return ofExt(lower.substring(dot + 1));
        return null;
    }
}