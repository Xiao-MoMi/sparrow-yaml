package net.momirealms.sparrow.yaml.serializer.auto.accessor;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FieldAccessors {
    private static final ClassValue<Map<FieldSignature, FieldAccessor>> CACHE = new ClassValue<>() {
        @Override
        protected Map<FieldSignature, FieldAccessor> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private FieldAccessors() {
    }

    public static FieldAccessor of(Field field) {
        return CACHE.get(field.getDeclaringClass()).computeIfAbsent(FieldSignature.of(field), ignored -> create(field));
    }

    private static FieldAccessor create(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new AutoSerializerException("Static field access is not supported: "
                    + field.getDeclaringClass().getName() + "." + field.getName());
        }
        if (Modifier.isFinal(field.getModifiers())) {
            return createFinal(field);
        }

        FieldAccessor methodHandle = methodHandleAccessor(field, true);
        if (methodHandle != null) {
            return methodHandle;
        }
        FieldAccessor unsafe = unsafeAccessor(field);
        if (unsafe != null) {
            return unsafe;
        }
        return reflectiveAccessor(field);
    }

    private static FieldAccessor createFinal(Field field) {
        FieldAccessor reader = methodHandleAccessor(field, false);
        if (reader == null) {
            reader = unsafeAccessor(field);
        }
        if (reader == null) {
            reader = reflectiveAccessor(field);
        }
        return new FinalFieldAccessor(field, reader, unsafeAccessor(field));
    }

    private static FieldAccessor methodHandleAccessor(Field field, boolean writable) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup());
            MethodHandle getter = lookup.unreflectGetter(field);
            MethodHandle setter = writable ? lookup.unreflectSetter(field) : null;
            return new MethodHandleFieldAccessor(field, getter, setter);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static FieldAccessor unsafeAccessor(Field field) {
        if (!UnsafeSupport.isAvailable()) {
            return null;
        }
        try {
            return new UnsafeFieldAccessor(field, UnsafeSupport.objectFieldOffset(field));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FieldAccessor reflectiveAccessor(Field field) {
        try {
            if (!field.trySetAccessible()) {
                throw new AutoSerializerException("Cannot access field "
                        + field.getDeclaringClass().getName() + "." + field.getName());
            }
            return new ReflectiveFieldAccessor(field);
        } catch (RuntimeException e) {
            throw new AutoSerializerException("Cannot access field "
                    + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }

    private record FieldSignature(String name, Class<?> type) {
        static FieldSignature of(Field field) {
            return new FieldSignature(field.getName(), field.getType());
        }
    }

    private static final class MethodHandleFieldAccessor implements FieldAccessor {
        private final Field field;
        private final MethodHandle getter;
        private final MethodHandle setter;

        private MethodHandleFieldAccessor(Field field, MethodHandle getter, MethodHandle setter) {
            this.field = field;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Object get(Object target) {
            try {
                return getter.invoke(target);
            } catch (Throwable e) {
                throw new AutoSerializerException("Cannot read field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public void set(Object target, Object value) {
            if (setter == null) {
                throw new AutoSerializerException("Cannot set final field "
                        + field.getDeclaringClass().getName() + "." + field.getName()
                        + "; prefer constructor binding for immutable fields");
            }
            try {
                setter.invoke(target, value);
            } catch (Throwable e) {
                throw new AutoSerializerException("Cannot set field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public boolean canSet() {
            return setter != null;
        }
    }

    private static final class UnsafeFieldAccessor implements FieldAccessor {
        private final Field field;
        private final long offset;
        private final Class<?> type;

        private UnsafeFieldAccessor(Field field, long offset) {
            this.field = field;
            this.offset = offset;
            this.type = field.getType();
        }

        @Override
        public Object get(Object target) {
            return UnsafeSupport.get(target, offset, type);
        }

        @Override
        public void set(Object target, Object value) {
            try {
                UnsafeSupport.set(target, offset, type, value);
            } catch (RuntimeException e) {
                throw new AutoSerializerException("Cannot set field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public boolean canSet() {
            return true;
        }
    }

    private static final class ReflectiveFieldAccessor implements FieldAccessor {
        private final Field field;

        private ReflectiveFieldAccessor(Field field) {
            this.field = field;
        }

        @Override
        public Object get(Object target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new AutoSerializerException("Cannot read field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public void set(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new AutoSerializerException("Cannot set field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public boolean canSet() {
            return true;
        }
    }

    private static final class FinalFieldAccessor implements FieldAccessor {
        private final Field field;
        private final FieldAccessor reader;
        private final FieldAccessor unsafeFallback;

        private FinalFieldAccessor(Field field, FieldAccessor reader, FieldAccessor unsafeFallback) {
            this.field = field;
            this.reader = reader;
            this.unsafeFallback = unsafeFallback;
            this.field.trySetAccessible();
        }

        @Override
        public Object get(Object target) {
            return reader.get(target);
        }

        @Override
        public void set(Object target, Object value) {
            try {
                field.set(target, value);
                return;
            } catch (IllegalAccessException | IllegalArgumentException ignored) {
            }
            if (unsafeFallback == null) {
                throw new AutoSerializerException("Cannot set final field "
                        + field.getDeclaringClass().getName() + "." + field.getName()
                        + "; prefer constructor binding for immutable fields");
            }
            unsafeFallback.set(target, value);
        }

        @Override
        public boolean canSet() {
            return true;
        }
    }
}
