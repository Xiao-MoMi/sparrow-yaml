package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.Objects;
import java.util.function.Function;

/**
 * mapping builder 中的单个字段声明.
 */
public final class Field<A> {
    private final String name; // YAML 字段名
    private final NodeSerializer<A> serializer; // 字段值 serializer
    private final boolean hasDefault; // 缺失时是否有固定默认值
    private final A defaultValue; // 缺失字段的固定默认值
    private final boolean optional; // 缺失时是否允许 null
    private final Function<? super RuntimeException, ? extends A> failureHandler; // 缺失或错误值的兜底函数

    public Field(
            String name,
            NodeSerializer<A> serializer,
            boolean hasDefault,
            A defaultValue,
            boolean optional,
            Function<? super RuntimeException, ? extends A> failureHandler
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.hasDefault = hasDefault;
        this.defaultValue = defaultValue;
        this.optional = optional;
        this.failureHandler = failureHandler;
    }

    /**
     * 字段缺失时使用固定默认值.
     */
    public Field<A> defaulted(A value) {
        return new Field<>(name, serializer, true, value, false, failureHandler);
    }

    /**
     * 字段缺失时返回 null.
     */
    public Field<A> optional() {
        return new Field<>(name, serializer, false, null, true, failureHandler);
    }

    /**
     * 字段缺失或字段值错误时, 调用 handler 生成兜底值.
     */
    public Field<A> onFail(Function<? super RuntimeException, ? extends A> handler) {
        return new Field<>(name, serializer, hasDefault, defaultValue, optional, Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 绑定编码时从目标对象读取字段值的 getter.
     */
    public <T> FieldComponent<T, A> forGetter(Function<? super T, ? extends A> getter) {
        return new FieldComponent<>(name, serializer, hasDefault, defaultValue, optional, failureHandler, getter);
    }
}
