package com.omni.business.assistance.importfile.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    private String targetRoot;
    private int total;
    private int success;
    private int skipped;
    private int failed;
    private List<Item> items = new ArrayList<>();

    public static class Item {
        private String path;
        private String status;   // SUCCESS / SKIPPED / FAILED
        private String message;
        private long size;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }

    public String getTargetRoot() { return targetRoot; }
    public void setTargetRoot(String targetRoot) { this.targetRoot = targetRoot; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
}