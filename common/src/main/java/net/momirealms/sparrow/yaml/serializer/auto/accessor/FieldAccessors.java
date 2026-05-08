package net.momirealms.sparrow.yaml.serializer.auto.accessor;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段访问器工厂.
 *
 * <p>非 final 字段使用 ASM hidden nestmate 访问器, 避免反射读写开销.
 * final 字段不能由外部生成类执行 {@code PUTFIELD}, 因此使用 {@link Field#set(Object, Object)}
 * 作为 fallback, 同时避免重新引入 Unsafe field offset.</p>
 */
public final class FieldAccessors {
    private static final Map<Field, FieldAccessor> CACHE = new ConcurrentHashMap<>(); // 每个 Field 只生成一个访问器

    private FieldAccessors() {
    }

    /**
     * 获取指定字段对应的访问器, 结果会按 Field 实例缓存.
     *
     * @param field 需要访问的反射字段
     * @return 字段访问器
     */
    public static FieldAccessor of(Field field) {
        return CACHE.computeIfAbsent(field, FieldAccessors::create);
    }

    /**
     * 根据字段修饰符选择访问策略.
     */
    private static FieldAccessor create(Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            return new ReflectiveFinalFieldAccessor(field);
        }
        return AsmFieldAccessorGenerator.generate(field);
    }

    /**
     * final 字段的反射 fallback.
     *
     * <p>JVM 不允许 hidden nestmate 从声明类外部对 final 字段执行 {@code PUTFIELD},
     * 所以 final 字段写入只能走反射或构造器绑定.</p>
     */
    private static final class ReflectiveFinalFieldAccessor implements FieldAccessor {
        private final Field field; // 被访问的 final 字段

        private ReflectiveFinalFieldAccessor(Field field) {
            this.field = field;
            this.field.setAccessible(true);
        }

        @Override
        public Object get(Object target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new AutoSerializerException("Cannot read final field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public void set(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
                throw new AutoSerializerException("Cannot set final field "
                        + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }

        @Override
        public boolean canSet() {
            return true;
        }
    }
}
