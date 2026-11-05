package com.omni.framework.converter.builtin;

import com.omni.framework.converter.ConvertException;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.converter.Converter;
import com.omni.framework.converter.FormatType;
import com.omni.framework.spring.annotation.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 图片转换器：PNG / JPG / BMP / GIF 互转，以及图片 → PDF（JDK 手写 PDF，零依赖）。
 */
@Component
public class ImageConverter implements Converter {

    @Override public String getName() { return "image"; }

    @Override public Set<FormatType> supportedSources() {
        return EnumSet.of(FormatType.PNG, FormatType.JPG, FormatType.BMP, FormatType.GIF);
    }

    @Override public Set<FormatType> supportedTargets() {
        return EnumSet.of(FormatType.PNG, FormatType.JPG, FormatType.BMP, FormatType.GIF, FormatType.PDF);
    }

    @Override
    public ConvertResult convert(ConvertRequest req) throws ConvertException {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(req.getSourceBytes()));
            if (img == null) throw new ConvertException("不支持的图片格式或文件损坏");
            String base = req.baseName();

            if (req.getTargetType() == FormatType.PDF) {
                return ConvertResult.okBytes(FormatType.PDF, imageToPdf(img), base + ".pdf");
            }
            String formatName = req.getTargetType() == FormatType.JPG ? "jpg" : req.getTargetType().ext;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!ImageIO.write(img, formatName, bos))
                throw new ConvertException("当前 JVM 不支持输出 " + formatName);
            return ConvertResult.okBytes(req.getTargetType(), bos.toByteArray(),
                    base + "." + req.getTargetType().ext);
        } catch (ConvertException e) {
            throw e;
        } catch (Exception e) {
            throw new ConvertException("图片转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 极简 PDF 生成器：单图单页，A4 居中。纯手写 PDF，零第三方依赖。
     */
    private byte[] imageToPdf(BufferedImage img) throws IOException {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, Color.WHITE, null);
        g.dispose();
        ByteArrayOutputStream jpgBuf = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", jpgBuf);
        byte[] jpg = jpgBuf.toByteArray();

        int pw = 595, ph = 842; // A4 @ 72dpi
        double ratio = Math.min((double) pw / img.getWidth(), (double) ph / img.getHeight());
        int iw = (int) (img.getWidth() * ratio);
        int ih = (int) (img.getHeight() * ratio);
        int ix = (pw - iw) / 2;
        int iy = (ph - ih) / 2;

        String content = "q\n" + iw + " 0 0 " + ih + " " + ix + " " + iy + " cm\n/Im0 Do\nQ\n";
        byte[] contentBytes = content.getBytes("ISO-8859-1");

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        pdf.write("%PDF-1.4\n".getBytes("ISO-8859-1"));

        offsets.add(pdf.size());
        pdf.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes("ISO-8859-1"));

        offsets.add(pdf.size());
        pdf.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes("ISO-8859-1"));

        offsets.add(pdf.size());
        pdf.write(("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                + pw + " " + ph + "] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")
                .getBytes("ISO-8859-1"));

        offsets.add(pdf.size());
        pdf.write(("4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + img.getWidth()
                + " /Height " + img.getHeight() + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                + " /Filter /DCTDecode /Length " + jpg.length + " >>\nstream\n").getBytes("ISO-8859-1"));
        pdf.write(jpg);
        pdf.write("\nendstream\nendobj\n".getBytes("ISO-8859-1"));

        offsets.add(pdf.size());
        pdf.write(("5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n").getBytes("ISO-8859-1"));
        pdf.write(contentBytes);
        pdf.write("\nendstream\nendobj\n".getBytes("ISO-8859-1"));

        int xref = pdf.size();
        StringBuilder tr = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int off : offsets) tr.append(String.format("%010d 00000 n \n", off));
        tr.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        pdf.write(tr.toString().getBytes("ISO-8859-1"));
        return pdf.toByteArray();
    }
}