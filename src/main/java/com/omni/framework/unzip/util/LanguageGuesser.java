package com.omni.framework.unzip.util;

public final class LanguageGuesser {
    private LanguageGuesser() {}

    public static String guess(String path) {
        if (path == null) return "text";
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "text";
        String ext = path.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "java": return "java";
            case "js":   return "javascript";
            case "ts":   return "typescript";
            case "html": case "htm": return "html";
            case "css":  return "css";
            case "xml":  return "xml";
            case "json": return "json";
            case "yml": case "yaml": return "yaml";
            case "md":   return "markdown";
            case "sql":  return "sql";
            case "py":   return "python";
            case "sh":   return "shell";
            case "properties": return "properties";
            default: return "text";
        }
    }

    public static boolean isBinary(String path) {
        if (path == null) return false;
        int dot = path.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = path.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "jar": case "class": case "zip": case "gz": case "tar": case "tgz":
            case "png": case "jpg": case "jpeg": case "gif": case "bmp": case "ico":
            case "webp": case "pdf": case "exe": case "dll": case "so": case "dylib":
            case "bin": case "mp3": case "mp4": case "wav": case "flac":
            case "ttf": case "woff": case "woff2": case "eot":
            case "xlsx": case "xls": case "docx": case "doc": case "pptx": case "ppt":
                return true;
            default: return false;
        }
    }
}