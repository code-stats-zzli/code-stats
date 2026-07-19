package com.omni.framework.jstats.core.model;

import java.util.List;

public class HeapHistogram {
    private final List<ClassEntry> entries;
    public HeapHistogram(List<ClassEntry> entries) { this.entries = entries; }
    public List<ClassEntry> getEntries() { return entries; }

    public static class ClassEntry {
        private final int index, instances;
        private final long bytes;
        private final String className;
        public ClassEntry(int index, int instances, long bytes, String className) {
            this.index = index; this.instances = instances; this.bytes = bytes; this.className = className;
        }
        public int getIndex() { return index; }
        public int getInstances() { return instances; }
        public long getBytes() { return bytes; }
        public String getClassName() { return className; }
    }
}