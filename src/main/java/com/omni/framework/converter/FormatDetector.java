package com.omni.framework.converter;

/**
 * 格式探测器：扩展名 → magic number。
 */
public final class FormatDetector {

    private FormatDetector() {}

    public static FormatType detect(String fileName, byte[] head) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                FormatType t = FormatType.ofExt(fileName.substring(dot + 1));
                if (t != null) return t;
            }
        }
        if (head != null && head.length >= 4) {
            if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G')
                return FormatType.PNG;
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8)
                return FormatType.JPG;
            if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8')
                return FormatType.GIF;
            if (head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F')
                return FormatType.PDF;
            if (head[0] == 'B' && head[1] == 'M')
                return FormatType.BMP;
        }
        return null;
    }
}