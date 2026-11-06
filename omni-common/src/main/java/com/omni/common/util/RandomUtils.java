package com.omni.common.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机 / UUID 工具类。
 */
public final class RandomUtils {

    private RandomUtils() {}

    /** 无横线的 32 位 UUID */
    public static String uuidNoDash() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 标准 UUID */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    public static int nextInt(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    public static long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** 随机字母数字串 */
    public static String alphanumeric(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}