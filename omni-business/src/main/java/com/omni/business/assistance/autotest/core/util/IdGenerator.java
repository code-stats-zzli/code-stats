package com.omni.business.assistance.autotest.core.util;


import java.util.UUID;

public class IdGenerator {
    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}
