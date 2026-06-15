package com.omni.common.excel;


import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Excel 处理工具类
 */
public class ExcelUtil {

    /**
     * 解析 Excel 文件，将每个 sheet 的内容转换为 CSV 格式的字符串列表（每行一个 CSV 行）
     * @param file Excel 文件
     * @return 每个 sheet 的 CSV 行列表（sheet 间用注释分隔）
     */
    public static List<String> parseExcelToCsvLines(File file) throws IOException {
        List<String> allLines = new ArrayList<>();
        String fileName = file.getName();
        allLines.add("// ======== 文件：" + fileName + " ========");

        try (InputStream is = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(is)) {
            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                allLines.add("// Sheet: " + sheet.getSheetName());
                if (sheet.getPhysicalNumberOfRows() == 0) continue;

                Iterator<Row> rowIterator = sheet.iterator();
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    List<String> cellValues = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = getCellValueAsString(cell);
                        // CSV 转义：包含逗号、换行、引号则用双引号包裹并转义内部引号
                        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                            value = "\"" + value.replace("\"", "\"\"") + "\"";
                        }
                        cellValues.add(value);
                    }
                    allLines.add(String.join(",", cellValues));
                }
                allLines.add(""); // 空行分隔 sheet
            }
        }
        return allLines;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    if (val == (long) val) return String.valueOf((long) val);
                    return String.valueOf(val);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }

    /**
     * 将 CSV 行写入文件（覆盖）
     */
    public static void writeCsvLinesToFile(Path outputFile, List<String> lines) throws IOException {
        Files.write(outputFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}