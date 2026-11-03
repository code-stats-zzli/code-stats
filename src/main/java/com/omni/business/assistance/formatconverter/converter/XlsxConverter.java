package com.omni.business.assistance.formatconverter.converter;

import com.omni.framework.converter.ConvertException;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.converter.Converter;
import com.omni.framework.converter.FormatType;
import com.omni.framework.spring.annotation.Component;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Excel 转换器（基于项目已有 Apache POI）：
 *   XLSX → CSV / TXT
 */
@Component
public class XlsxConverter implements Converter {

    @Override public String getName() { return "xlsx"; }

    @Override public Set<FormatType> supportedSources() {
        return EnumSet.of(FormatType.XLSX);
    }

    @Override public Set<FormatType> supportedTargets() {
        return EnumSet.of(FormatType.CSV, FormatType.TXT);
    }

    @Override
    public ConvertResult convert(ConvertRequest req) throws ConvertException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(req.getSourceBytes()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<List<String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    cells.add(fmt.formatCellValue(cell));
                }
                rows.add(cells);
            }
            String base = req.baseName();
            if (req.getTargetType() == FormatType.CSV) {
                return ConvertResult.okText(FormatType.CSV, toCsv(rows), base + ".csv");
            }
            return ConvertResult.okText(FormatType.TXT, toTxt(rows), base + ".txt");
        } catch (Exception e) {
            throw new ConvertException("XLSX 处理失败: " + e.getMessage(), e);
        }
    }

    private String toCsv(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> r : rows) {
            for (int i = 0; i < r.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(csvEsc(r.get(i)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String toTxt(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> r : rows) {
            sb.append(String.join("\t", r)).append('\n');
        }
        return sb.toString();
    }

    private String csvEsc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}