package com.omni.framework.codestats.dependency.model;

import java.util.Objects;

public class ClassInfo {
    private final String fullyQualifiedName;
    private final String packageName;
    private final String simpleName;
    private final ClassType type;

    public enum ClassType {
        CLASS, INTERFACE, ENUM, ANNOTATION
    }

    public ClassInfo(String fullyQualifiedName, String packageName, String simpleName, ClassType type) {
        this.fullyQualifiedName = fullyQualifiedName;
        this.packageName = packageName;
        this.simpleName = simpleName;
        this.type = type;
    }

    public String getFullyQualifiedName() { return fullyQualifiedName; }
    public String getPackageName() { return packageName; }
    public String getSimpleName() { return simpleName; }
    public ClassType getType() { return type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassInfo)) return false;
        ClassInfo that = (ClassInfo) o;
        return fullyQualifiedName.equals(that.fullyQualifiedName);
    }

    @Override
    public int hashCode() { return Objects.hash(fullyQualifiedName); }

    @Override
    public String toString() {
        return "ClassInfo{" + fullyQualifiedName + ", type=" + type + '}';
    }
}