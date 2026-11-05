package com.omni.framework.expression.util;

import com.omni.framework.expression.ExpressionException;

public final class TypeUtils {
    private TypeUtils() {}

    // ==================== 类型转换 ====================

    public static boolean toBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
        if (obj instanceof String) return !((String) obj).isEmpty();
        return true;
    }

    public static int toInteger(Object obj) {
        if (obj == null) throw new ExpressionException("Cannot convert null to int");
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
    }

    public static double toDouble(Object obj) {
        if (obj == null) throw new ExpressionException("Cannot convert null to double");
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.parseDouble(obj.toString());
    }

    // ==================== 算术运算（统一 null 检查） ====================

    private static void checkNull(Object a, Object b, String op) {
        if (a == null || b == null) {
            throw new ExpressionException("Cannot perform '" + op + "' operation on null value");
        }
    }

    public static Object add(Object a, Object b) {
        checkNull(a, b, "+");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() + ((Number) b).doubleValue();
        }
        if (a instanceof String || b instanceof String) {
            return String.valueOf(a) + String.valueOf(b);
        }
        throw new ExpressionException("Unsupported + operation: " + a.getClass() + ", " + b.getClass());
    }

    public static Object subtract(Object a, Object b) {
        checkNull(a, b, "-");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() - ((Number) b).doubleValue();
        }
        throw new ExpressionException("Unsupported - operation: " + a.getClass() + ", " + b.getClass());
    }

    public static Object multiply(Object a, Object b) {
        checkNull(a, b, "*");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() * ((Number) b).doubleValue();
        }
        throw new ExpressionException("Unsupported * operation: " + a.getClass() + ", " + b.getClass());
    }

    public static Object divide(Object a, Object b) {
        checkNull(a, b, "/");
        if (a instanceof Number && b instanceof Number) {
            double divisor = ((Number) b).doubleValue();
            if (divisor == 0) throw new ExpressionException("Division by zero");
            return ((Number) a).doubleValue() / divisor;
        }
        throw new ExpressionException("Unsupported / operation: " + a.getClass() + ", " + b.getClass());
    }

    public static Object modulus(Object a, Object b) {
        checkNull(a, b, "%");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() % ((Number) b).doubleValue();
        }
        throw new ExpressionException("Unsupported % operation: " + a.getClass() + ", " + b.getClass());
    }

    // ==================== 位运算 ====================

    public static Object bitwiseAnd(Object a, Object b) {
        checkNull(a, b, "&");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).longValue() & ((Number) b).longValue();
        }
        throw new ExpressionException("Unsupported & operation");
    }

    public static Object bitwiseOr(Object a, Object b) {
        checkNull(a, b, "|");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).longValue() | ((Number) b).longValue();
        }
        throw new ExpressionException("Unsupported | operation");
    }

    public static Object bitwiseXor(Object a, Object b) {
        checkNull(a, b, "^");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).longValue() ^ ((Number) b).longValue();
        }
        throw new ExpressionException("Unsupported ^ operation");
    }

    public static Object shiftLeft(Object a, Object b) {
        checkNull(a, b, "<<");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).longValue() << ((Number) b).intValue();
        }
        throw new ExpressionException("Unsupported << operation");
    }

    public static Object shiftRight(Object a, Object b) {
        checkNull(a, b, ">>");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).longValue() >> ((Number) b).intValue();
        }
        throw new ExpressionException("Unsupported >> operation");
    }

    public static Object unsignedShiftRight(Object a, Object b) {
        checkNull(a, b, ">>>");
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).longValue() >>> ((Number) b).intValue();
        }
        throw new ExpressionException("Unsupported >>> operation");
    }

    // ==================== 逻辑运算（&& || 在 StandardEvaluator 中已用 toBoolean，无需改） ====================

    // ==================== 比较运算 ====================

    public static boolean equals(Object a, Object b) {
        // 比较特殊：null == null 返回 true，其他情况返回 false（不抛异常）
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public static int compare(Object a, Object b) {
        if (a == null || b == null) {
            throw new ExpressionException("Cannot compare with null value");
        }
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b);
            } catch (ClassCastException e) {
                throw new ExpressionException("Cannot compare " + a.getClass() + " with " + b.getClass());
            }
        }
        throw new ExpressionException("Unsupported comparison");
    }

    // ==================== 一元运算 ====================

    public static Object negate(Object a) {
        if (a == null) throw new ExpressionException("Cannot negate null");
        if (a instanceof Number) return -((Number) a).doubleValue();
        throw new ExpressionException("Unsupported unary - on " + a.getClass());
    }

    public static Object plus(Object a) {
        if (a == null) throw new ExpressionException("Cannot apply unary + on null");
        if (a instanceof Number) return +((Number) a).doubleValue();
        throw new ExpressionException("Unsupported unary + on " + a.getClass());
    }

    public static Object bitwiseNot(Object a) {
        if (a == null) throw new ExpressionException("Cannot apply ~ on null");
        if (a instanceof Number) return ~((Number) a).longValue();
        throw new ExpressionException("Unsupported ~ on " + a.getClass());
    }
}