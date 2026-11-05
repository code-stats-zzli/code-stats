package com.omni.business.assistance.systemcheck.model;

/** 单条检查结果 */
public class Finding {
    private String category;
    private String item;
    private String status;      // 合规 / 不合规 / 警告 / 信息
    private String risk;        // 高 / 中 / 低 / 信息
    private String detail;
    private String suggestion = "";

    public Finding() {}
    public Finding(String category, String item, String status, String risk, String detail) {
        this.category = category; this.item = item; this.status = status;
        this.risk = risk; this.detail = detail;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
}