package com.omni.framework.converter;

/**
 * 转换过程业务异常。
 */
public class ConvertException extends Exception {
    public ConvertException(String msg) { super(msg); }
    public ConvertException(String msg, Throwable cause) { super(msg, cause); }
}