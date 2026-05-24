package net.momirealms.sparrow.yaml.serializer.auto.accessor;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

final class UnsafeSupport {
    private static final Unsafe UNSAFE = loadUnsafe();

    private UnsafeSupport() {
    }

    static boolean isAvailable() {
        return UNSAFE != null;
    }

    static long objectFieldOffset(Field field) {
        if (UNSAFE == null) {
            throw new IllegalStateException("Unsafe is not available");
        }
        return UNSAFE.objectFieldOffset(field);
    }

    static Object allocateInstance(Class<?> type) throws InstantiationException {
        if (UNSAFE == null) {
            throw new IllegalStateException("Unsafe is not available");
        }
        return UNSAFE.allocateInstance(type);
    }

    static Object get(Object target, long offset, Class<?> type) {
        if (type == int.class) {
            return UNSAFE.getInt(target, offset);
        }
        if (type == long.class) {
            return UNSAFE.getLong(target, offset);
        }
        if (type == double.class) {
            return UNSAFE.getDouble(target, offset);
        }
        if (type == float.class) {
            return UNSAFE.getFloat(target, offset);
        }
        if (type == boolean.class) {
            return UNSAFE.getBoolean(target, offset);
        }
        if (type == byte.class) {
            return UNSAFE.getByte(target, offset);
        }
        if (type == short.class) {
            return UNSAFE.getShort(target, offset);
        }
        if (type == char.class) {
            return UNSAFE.getChar(target, offset);
        }
        return UNSAFE.getObject(target, offset);
    }

    static void set(Object target, long offset, Class<?> type, Object value) {
        if (type == int.class) {
            UNSAFE.putInt(target, offset, value != null ? (Integer) value : 0);
            return;
        }
        if (type == long.class) {
            UNSAFE.putLong(target, offset, value != null ? (Long) value : 0L);
            return;
        }
        if (type == double.class) {
            UNSAFE.putDouble(target, offset, value != null ? (Double) value : 0D);
            return;
        }
        if (type == float.class) {
            UNSAFE.putFloat(target, offset, value != null ? (Float) value : 0F);
            return;
        }
        if (type == boolean.class) {
            UNSAFE.putBoolean(target, offset, value != null && (Boolean) value);
            return;
        }
        if (type == byte.class) {
            UNSAFE.putByte(target, offset, value != null ? (Byte) value : (byte) 0);
            return;
        }
        if (type == short.class) {
            UNSAFE.putShort(target, offset, value != null ? (Short) value : (short) 0);
            return;
        }
        if (type == char.class) {
            UNSAFE.putChar(target, offset, value != null ? (Character) value : (char) 0);
            return;
        }
        UNSAFE.putObject(target, offset, value);
    }

    private static Unsafe loadUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
