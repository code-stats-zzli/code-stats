package com.omni.business.assistance.cluster.model;

import java.util.ArrayList;
import java.util.List;

/** 单台机器上的 JDK 探明结果。 */
public class JdkInfo {
    private String currentJavaPath;     // 当前 java 命令对应的路径
    private String currentVersion;      // 8 / 11 / 17 / 21 ...
    private List<Candidate> candidates = new ArrayList<>();
    private String error;

    public static class Candidate {
        private String path;
        private String version;
        public Candidate() {}
        public Candidate(String path, String version) { this.path = path; this.version = version; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public String getCurrentJavaPath() { return currentJavaPath; }
    public void setCurrentJavaPath(String currentJavaPath) { this.currentJavaPath = currentJavaPath; }
    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
    public List<Candidate> getCandidates() { return candidates; }
    public void setCandidates(List<Candidate> candidates) { this.candidates = candidates; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}