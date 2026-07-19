// core/model/JvmOption.java
package com.omni.framework.jstats.core.model;

public class JvmOption {
    private final String name, value;
    public JvmOption(String name, String value) { this.name = name; this.value = value; }
    public String getName() { return name; }
    public String getValue() { return value; }
}