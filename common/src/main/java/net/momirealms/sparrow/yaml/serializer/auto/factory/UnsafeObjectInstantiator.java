package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

final class UnsafeObjectInstantiator implements ObjectInstantiator {
    private static final Unsafe UNSAFE = loadUnsafe();

    private final Class<?> type;

    UnsafeObjectInstantiator(Class<?> type) {
        if (UNSAFE == null) {
            throw new AutoSerializerException("Unsafe is not available for " + type.getName());
        }
        this.type = type;
    }

    @Override
    public Object instantiate(Object[] arguments) {
        try {
            return UNSAFE.allocateInstance(type);
        } catch (InstantiationException e) {
            throw new AutoSerializerException("Cannot allocate " + type.getName(), e);
        }
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
