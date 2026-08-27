package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可切换行号的多行表格数据解析器。
 * <p>
 * 适用场景：
 * <ul>
 *   <li>报表引擎：遍历多行数据，每行用相同表达式计算</li>
 *   <li>Excel/CSV 解析：逐行处理数据</li>
 *   <li>数据看板：通过行号切换查看不同行</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * List<String> headers = Arrays.asList("name", "age", "salary");
 * List<Object[]> rows = Arrays.asList(
 *     new Object[]{"张三", 25, 15000.0},
 *     new Object[]{"李四", 30, 20000.0},
 *     new Object[]{"王五", 28, 18000.0}
 * );
 *
 * SwitchableRowsResolver resolver = new SwitchableRowsResolver(headers, rows);
 *
 * // 切换到第2行（索引1）
 * resolver.setRowIndex(1);
 * resolver.resolve("name", context);   // "李四"
 *
 * // 切换到第3行（索引2）
 * resolver.setRowIndex(2);
 * resolver.resolve("salary", context); // 18000.0
 *
 * // 链式操作
 * resolver.setRowIndex(0).resolve("age", context); // 25
 * </pre>
 */
public class SwitchableRowsResolver implements VariableResolver {

    // 列名 → 索引映射（O(1) 查找）
    private final Map<String, Integer> columnIndexMap = new HashMap<>();

    // 所有数据行
    private final List<Object[]> allRows;

    // 当前行索引（-1 表示无有效行）
    private int currentRowIndex = -1;

    // 当前行的缓存（避免重复从 List 中取）
    private Object[] currentRowData = null;

    /**
     * 构造可切换行号的表格解析器。
     *
     * @param headers 列头列表（顺序与数据行数组下标对应）
     * @param rows    所有数据行（List 中每个元素是一行 Object[]）
     */
    public SwitchableRowsResolver(List<String> headers, List<Object[]> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("headers cannot be null or empty");
        }
        // 建立列名 → 索引映射
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null) {
                columnIndexMap.put(header, i);
            }
        }
        this.allRows = (rows != null) ? rows : List.of();
        // 默认切换到第一行（如果有数据）
        if (!this.allRows.isEmpty()) {
            setRowIndex(0);
        }
    }

    // ==================== 行号切换 ====================

    /**
     * 设置当前行索引（从0开始）。
     *
     * @param index 行索引
     * @return 当前解析器（支持链式调用）
     * @throws IndexOutOfBoundsException 如果索引越界
     */
    public SwitchableRowsResolver setRowIndex(int index) {
        if (index < 0 || index >= allRows.size()) {
            throw new IndexOutOfBoundsException(
                    "Row index " + index + " out of bounds, total rows: " + allRows.size()
            );
        }
        this.currentRowIndex = index;
        this.currentRowData = allRows.get(index);
        return this;
    }

    /**
     * 切换到下一行（如果存在）。
     *
     * @return true 如果成功切换到下一行，false 如果已是最后一行
     */
    public boolean nextRow() {
        if (currentRowIndex < allRows.size() - 1) {
            setRowIndex(currentRowIndex + 1);
            return true;
        }
        return false;
    }

    /**
     * 切换到上一行（如果存在）。
     *
     * @return true 如果成功切换到上一行，false 如果已是第一行
     */
    public boolean previousRow() {
        if (currentRowIndex > 0) {
            setRowIndex(currentRowIndex - 1);
            return true;
        }
        return false;
    }

    /**
     * 重置到第一行。
     */
    public SwitchableRowsResolver resetToFirst() {
        if (!allRows.isEmpty()) {
            setRowIndex(0);
        }
        return this;
    }

    /**
     * 清除当前行数据（设置 currentRowIndex = -1，currentRowData = null）。
     */
    public SwitchableRowsResolver clearRow() {
        this.currentRowIndex = -1;
        this.currentRowData = null;
        return this;
    }

    // ==================== 数据访问 ====================

    /**
     * 获取当前行索引。
     */
    public int getCurrentRowIndex() {
        return currentRowIndex;
    }

    /**
     * 获取总行数。
     */
    public int getRowCount() {
        return allRows.size();
    }

    /**
     * 获取当前行数据（只读拷贝）。
     */
    public Object[] getCurrentRowData() {
        return currentRowData != null ? currentRowData.clone() : null;
    }

    /**
     * 获取当前行指定列的值（不通过表达式上下文，直接取值）。
     */
    public Object getValue(String columnName) {
        if (currentRowData == null) {
            return null;
        }
        Integer index = columnIndexMap.get(columnName);
        if (index == null || index < 0 || index >= currentRowData.length) {
            return null;
        }
        return currentRowData[index];
    }

    /**
     * 获取完整行列表（只读）。
     */
    public List<Object[]> getAllRows() {
        return allRows;
    }

    /**
     * 获取列名映射（只读）。
     */
    public Map<String, Integer> getColumnIndexMap() {
        return new HashMap<>(columnIndexMap);
    }

    // ==================== VariableResolver 接口实现 ====================

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (currentRowData == null) {
            return null;
        }
        Integer index = columnIndexMap.get(name);
        if (index == null || index < 0 || index >= currentRowData.length) {
            return null;
        }
        return currentRowData[index];
    }
}
