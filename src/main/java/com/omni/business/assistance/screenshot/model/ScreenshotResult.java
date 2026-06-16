package com.omni.business.assistance.screenshot.model;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

public class ScreenshotResult {
    private BufferedImage image;
    private String recognizedText;
    private boolean found;
    private List<Map<String, String>> matchedRows;  // 多条匹配结果
    private List<String> columns;                   // 列名列表

    // getters & setters
    public BufferedImage getImage() { return image; }
    public void setImage(BufferedImage image) { this.image = image; }
    public String getRecognizedText() { return recognizedText; }
    public void setRecognizedText(String recognizedText) { this.recognizedText = recognizedText; }
    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }
    public List<Map<String, String>> getMatchedRows() { return matchedRows; }
    public void setMatchedRows(List<Map<String, String>> matchedRows) { this.matchedRows = matchedRows; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
}