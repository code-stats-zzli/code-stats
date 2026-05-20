// 文件路径: src/main/java/com/omni/framework/util/TypeConverter.java
package com.omni.framework.util;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.Date;

/**
 * 类型转换工具类，支持常见类型之间的转换，包括：
 * - 基础类型及其包装类
 * - String 与 Number/Boolean 互转
 * - Java 8 时间类型 (LocalDateTime, LocalDate, LocalTime) 与 java.util.Date/java.sql.Timestamp/Date/Time 互转
 * - Integer/Long/Double 等与 String 互转
 */
public final class TypeConverter {

    private TypeConverter() {}

    /**
     * 将 value 转换为 targetType 类型的对象
     * @param value 原始值，可能为 null
     * @param targetType 目标类型
     * @param <T> 目标类型泛型
     * @return 转换后的对象，若 value 为 null 则返回 null
     * @throws IllegalArgumentException 如果转换不支持
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }

        // 1. 如果目标类型与当前类型相同，直接返回
        if (targetType.isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        // 2. 处理 String 到其他基本类型
        if (value instanceof String) {
            String str = (String) value;
            if (targetType == Integer.class || targetType == int.class) {
                return (T) Integer.valueOf(str);
            }
            if (targetType == Long.class || targetType == long.class) {
                return (T) Long.valueOf(str);
            }
            if (targetType == Short.class || targetType == short.class) {
                return (T) Short.valueOf(str);
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return (T) Byte.valueOf(str);
            }
            if (targetType == Float.class || targetType == float.class) {
                return (T) Float.valueOf(str);
            }
            if (targetType == Double.class || targetType == double.class) {
                return (T) Double.valueOf(str);
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return (T) Boolean.valueOf(str);
            }
            if (targetType == Character.class || targetType == char.class) {
                if (str.length() == 1) {
                    return (T) Character.valueOf(str.charAt(0));
                }
                throw new IllegalArgumentException("无法将字符串转换为 char: " + str);
            }
            if (targetType == Date.class) {
                try {
                    return (T) Timestamp.valueOf(str);
                } catch (Exception e) {
                    throw new IllegalArgumentException("无法将字符串转换为 Date: " + str, e);
                }
            }
            if (targetType == LocalDateTime.class) {
                try {
                    return (T) LocalDateTime.parse(str);
                } catch (Exception e) {
                    throw new IllegalArgumentException("无法将字符串转换为 LocalDateTime: " + str, e);
                }
            }
            if (targetType == LocalDate.class) {
                try {
                    return (T) LocalDate.parse(str);
                } catch (Exception e) {
                    throw new IllegalArgumentException("无法将字符串转换为 LocalDate: " + str, e);
                }
            }
            if (targetType == LocalTime.class) {
                try {
                    return (T) LocalTime.parse(str);
                } catch (Exception e) {
                    throw new IllegalArgumentException("无法将字符串转换为 LocalTime: " + str, e);
                }
            }
        }

        // 3. 处理 Number 类型（Integer, Long, Double, Float 等）到 String
        if (value instanceof Number) {
            if (targetType == String.class) {
                return (T) value.toString();
            }
        }

        // 4. 处理 Boolean 到 String
        if (value instanceof Boolean) {
            if (targetType == String.class) {
                return (T) value.toString();
            }
        }

        // 5. 处理 Character 到 String
        if (value instanceof Character) {
            if (targetType == String.class) {
                return (T) String.valueOf(value);
            }
        }

        // 6. 处理 LocalDateTime <-> Date/Timestamp
        if (value instanceof LocalDateTime) {
            LocalDateTime ldt = (LocalDateTime) value;
            ZoneId zone = ZoneId.systemDefault();
            if (targetType == Date.class) {
                return (T) Date.from(ldt.atZone(zone).toInstant());
            }
            if (targetType == Timestamp.class) {
                return (T) Timestamp.from(ldt.atZone(zone).toInstant());
            }
            if (targetType == java.sql.Date.class) {
                return (T) new java.sql.Date(ldt.atZone(zone).toInstant().toEpochMilli());
            }
        }

        // 7. 处理 LocalDate <-> Date/java.sql.Date
        if (value instanceof LocalDate) {
            LocalDate ld = (LocalDate) value;
            ZoneId zone = ZoneId.systemDefault();
            Instant instant = ld.atStartOfDay(zone).toInstant();
            if (targetType == Date.class) {
                return (T) Date.from(instant);
            }
            if (targetType == java.sql.Date.class) {
                return (T) new java.sql.Date(instant.toEpochMilli());
            }
            if (targetType == Timestamp.class) {
                return (T) Timestamp.from(instant);
            }
            if (targetType == LocalDateTime.class) {
                return (T) ld.atStartOfDay();
            }
        }

        // 8. 处理 LocalTime <-> Time
        if (value instanceof LocalTime) {
            if (targetType == Time.class) {
                return (T) Time.valueOf((LocalTime) value);
            }
        }

        // 9. 处理 Date/Long -> LocalDateTime/LocalDate/LocalTime
        long millis = -1;
        if (value instanceof Date) {
            millis = ((Date) value).getTime();
        } else if (value instanceof Timestamp) {
            millis = ((Timestamp) value).getTime();
        } else if (value instanceof Long) {
            millis = (Long) value;
        }
        if (millis != -1) {
            Instant instant = Instant.ofEpochMilli(millis);
            ZoneId zone = ZoneId.systemDefault();
            if (targetType == LocalDateTime.class) {
                return (T) LocalDateTime.ofInstant(instant, zone);
            }
            if (targetType == LocalDate.class) {
                return (T) LocalDateTime.ofInstant(instant, zone).toLocalDate();
            }
            if (targetType == LocalTime.class) {
                return (T) LocalDateTime.ofInstant(instant, zone).toLocalTime();
            }
        }

        // 10. 处理 Date <-> Timestamp
        if (value instanceof Date && targetType == Timestamp.class) {
            return (T) new Timestamp(((Date) value).getTime());
        }
        if (value instanceof Timestamp && targetType == Date.class) {
            return (T) new Date(((Timestamp) value).getTime());
        }

        // 11. 基本类型之间的转换（如 Integer -> Long, Long -> Integer 等）
        if (value instanceof Number && Number.class.isAssignableFrom(targetType)) {
            Number num = (Number) value;
            if (targetType == Integer.class || targetType == int.class) {
                return (T) Integer.valueOf(num.intValue());
            }
            if (targetType == Long.class || targetType == long.class) {
                return (T) Long.valueOf(num.longValue());
            }
            if (targetType == Double.class || targetType == double.class) {
                return (T) Double.valueOf(num.doubleValue());
            }
            if (targetType == Float.class || targetType == float.class) {
                return (T) Float.valueOf(num.floatValue());
            }
            if (targetType == Short.class || targetType == short.class) {
                return (T) Short.valueOf(num.shortValue());
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return (T) Byte.valueOf(num.byteValue());
            }
        }

        throw new IllegalArgumentException("Unsupported conversion: " + value.getClass().getName() + " -> " + targetType.getName());
    }
}