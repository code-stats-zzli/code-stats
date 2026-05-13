package com.framework.util;

import com.framework.jdbc.metedata.datamodel.ColumnDef;
import com.framework.jdbc.metedata.datamodel.DataModel;
import com.framework.jdbc.metedata.datamodel.Row;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DataModel 多场景转换工具，基于 JsonUtil 实现 JSON 序列化，
 * 同时支持 CSV、HTML、Markdown 等常用格式。
 */
public class DataModelConverter {

    /**
     * 将完整 DataModel（包含列定义和所有行）转换为 JSON 对象字符串。
     * 格式：{"columns":[...], "rows":[...]}
     */
    public static String toFullJson(DataModel dm) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("columns", columnsToJson(dm));
        root.put("rows", rowsToJsonArray(dm, null)); // 所有列
        root.put("rowCount", dm.getRowCount());
        return JsonUtil.toJson(root);
    }

    /**
     * 将列定义转换为 JSON 数组，每个元素包含列名、类型等信息。
     */
    public static String columnsToJson(DataModel dm) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ColumnDef col : dm.getColumnDefs()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", col.getName());
            map.put("typeName", col.getTypeName());
            map.put("sqlType", col.getSqlType());
            map.put("nullable", col.isNullable());
            map.put("columnSize", col.getColumnSize());
            map.put("decimalDigits", col.getDecimalDigits());
            map.put("remarks", col.getRemarks());
            list.add(map);
        }
        return JsonUtil.toJson(list);
    }

    /**
     * 将所有行数据转换为 JSON 数组，每行是一个 JSON 对象，键为列名。
     */
    public static String rowsToJsonArray(DataModel dm) {
        return rowsToJsonArray(dm, null);
    }

    /**
     * 将指定列集的行数据转换为 JSON 数组。
     *
     * @param dm           数据模型
     * @param columnNames  需要包含的列名集合，为 null 或空则包含所有列
     */
    public static String rowsToJsonArray(DataModel dm, Set<String> columnNames) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> effectiveColNames = resolveColumnNames(dm, columnNames);

        for (int i = 0; i < dm.getRowCount(); i++) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            Row row = dm.getRow(i);
            for (String colName : effectiveColNames) {
                Object value = row.getValue(colName);
                rowMap.put(colName, value);
            }
            rows.add(rowMap);
        }
        return JsonUtil.toJson(rows);
    }

    /**
     * 将数据模型转换为 CSV 字符串（不含列定义，纯数据）。
     * 第一行为表头，后续为数据行。
     */
    public static String toCsv(DataModel dm) {
        return toCsv(dm, null);
    }

    /**
     * 将指定列集的数据模型转换为 CSV 字符串。
     */
    public static String toCsv(DataModel dm, Set<String> columnNames) {
        List<String> effectiveCols = resolveColumnNames(dm, columnNames);
        StringBuilder sb = new StringBuilder();

        // 表头
        sb.append(effectiveCols.stream().map(DataModelConverter::escapeCsv).collect(Collectors.joining(",")));
        sb.append("\n");

        // 数据行
        for (int i = 0; i < dm.getRowCount(); i++) {
            Row row = dm.getRow(i);
            String line = effectiveCols.stream()
                    .map(name -> {
                        Object val = row.getValue(name);
                        return val == null ? "" : escapeCsv(val.toString());
                    })
                    .collect(Collectors.joining(","));
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * 转换为 HTML 表格字符串（基础样式）。
     */
    public static String toHtmlTable(DataModel dm) {
        return toHtmlTable(dm, null, "");
    }

    /**
     * 转换为 HTML 表格，支持自定义列和附加 CSS 类名。
     */
    public static String toHtmlTable(DataModel dm, Set<String> columnNames, String tableClass) {
        List<String> effectiveCols = resolveColumnNames(dm, columnNames);
        StringBuilder sb = new StringBuilder();
        sb.append("<table");
        if (tableClass != null && !tableClass.isEmpty()) {
            sb.append(" class='").append(tableClass).append("'");
        }
        sb.append(" border='1' cellspacing='0' cellpadding='5'>");

        // 表头
        sb.append("<thead><tr>");
        for (String col : effectiveCols) {
            sb.append("<th>").append(col).append("</th>");
        }
        sb.append("</tr></thead><tbody>");

        // 数据行
        for (int i = 0; i < dm.getRowCount(); i++) {
            sb.append("<tr>");
            Row row = dm.getRow(i);
            for (String col : effectiveCols) {
                Object val = row.getValue(col);
                sb.append("<td>").append(val != null ? val.toString() : "").append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    /**
     * 转换为 Markdown 表格。
     */
    public static String toMarkdownTable(DataModel dm) {
        return toMarkdownTable(dm, null);
    }

    /**
     * 转换为 Markdown 表格，支持自定义列。
     */
    public static String toMarkdownTable(DataModel dm, Set<String> columnNames) {
        List<String> effectiveCols = resolveColumnNames(dm, columnNames);
        StringBuilder sb = new StringBuilder();

        // 表头
        sb.append("| ");
        sb.append(String.join(" | ", effectiveCols));
        sb.append(" |\n");

        // 分隔线
        sb.append("| ");
        sb.append(effectiveCols.stream().map(s -> "---").collect(Collectors.joining(" | ")));
        sb.append(" |\n");

        // 数据行
        for (int i = 0; i < dm.getRowCount(); i++) {
            Row row = dm.getRow(i);
            sb.append("| ");
            String line = effectiveCols.stream()
                    .map(name -> {
                        Object val = row.getValue(name);
                        return val == null ? "" : val.toString();
                    })
                    .collect(Collectors.joining(" | "));
            sb.append(line).append(" |\n");
        }
        return sb.toString();
    }

    /* ---------- 内部辅助方法 ---------- */

    private static List<String> resolveColumnNames(DataModel dm, Set<String> specifiedColumns) {
        if (specifiedColumns != null && !specifiedColumns.isEmpty()) {
            // 按照传入顺序返回，但保持与 model 中列定义顺序一致
            List<String> ordered = new ArrayList<>();
            for (String col : dm.getColumnNames()) {
                if (specifiedColumns.contains(col)) {
                    ordered.add(col);
                }
            }
            return ordered;
        } else {
            return Arrays.asList(dm.getColumnNames());
        }
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}