package com.codestats.framework.codestats.gui;

import com.codestats.framework.codestats.core.FileInfo;
import com.codestats.framework.codestats.core.StatisticsResult;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class FileInfoTableModel extends AbstractTableModel {
    private final String[] columns = {"Path", "Lines", "Code", "Comment", "Blank", "Language", "Complexity"};
    private List<FileInfo> fileInfos;

    {
        fileInfos = new ArrayList<>();
    }

    private boolean hasSummary = false;
    private StatisticsResult summaryResult;

    public void setFileInfos(List<FileInfo> infos, StatisticsResult result) {
        this.fileInfos = infos;
        this.hasSummary = (result != null);
        this.summaryResult = result;
        fireTableDataChanged();
    }

    public void clear() {
        List<FileInfo> infos = new ArrayList<>();
        setFileInfos(infos, null);
    }

    @Override
    public int getRowCount() {
        return fileInfos.size() + (hasSummary ? 1 : 0);
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (hasSummary && rowIndex == 0) {
            switch (columnIndex) {
                case 0: return "[Summary]";
                case 1: return summaryResult.getTotalLines();
                case 2: return summaryResult.getTotalCodeLines();
                case 3: return summaryResult.getTotalCommentLines();
                case 4: return summaryResult.getTotalBlankLines();
                case 5: return "";
                case 6: return "";
                default: return "";
            }
        }
        int dataIndex = hasSummary ? rowIndex - 1 : rowIndex;
        FileInfo info = fileInfos.get(dataIndex);
        switch (columnIndex) {
            case 0: return info.getPath().toString();
            case 1: return info.getLines();
            case 2: return info.getCodeLines();
            case 3: return info.getCommentLines();
            case 4: return info.getBlankLines();
            case 5: return info.getLanguage();
            case 6: return info.getComplexity();
            default: return null;
        }
    }
}