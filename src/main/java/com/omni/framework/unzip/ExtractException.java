package com.omni.framework.unzip;

public class ExtractException extends Exception {
    public ExtractException(String msg) { super(msg); }
    public ExtractException(String msg, Throwable cause) { super(msg, cause); }
}