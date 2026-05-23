package com.omni.framework.codestats.dependency.model;

import java.util.Set;

public  class ExtractedResult {
    private final ClassInfo classInfo;
    private final Set<String> dependencies;
    private final DependencyResult result;

    public ExtractedResult(ClassInfo classInfo, Set<String> dependencies, DependencyResult result) {
        this.classInfo = classInfo;
        this.dependencies = dependencies;
        this.result = result;
    }

    public ClassInfo getClassInfo() { return classInfo; }
    public Set<String> getDependencies() { return dependencies; }
    public DependencyResult getResult() { return result; }
}