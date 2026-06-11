package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.Objects;
import java.util.function.Function;

/**
 * sequence builder 中的单个元素声明.
 */
public final class Element<A> {
    private final int index; // YAML 序列下标
    private final NodeSerializer<A> serializer; // 元素值 serializer
    private final boolean hasDefault; // 缺失时是否有固定默认值
    private final A defaultValue; // 缺失元素的固定默认值
    private final boolean optional; // 缺失时是否允许 null
    private final Function<? super RuntimeException, ? extends A> failureHandler; // 缺失或错误值的兜底函数

    public Element(
            int index,
            NodeSerializer<A> serializer,
            boolean hasDefault,
            A defaultValue,
            boolean optional,
            Function<? super RuntimeException, ? extends A> failureHandler
    ) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        this.index = index;
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.hasDefault = hasDefault;
        this.defaultValue = defaultValue;
        this.optional = optional;
        this.failureHandler = failureHandler;
    }

    /**
     * 元素缺失时使用固定默认值, 元素存在但值错误时仍按失败处理.
     */
    public Element<A> defaulted(A value) {
        return new Element<>(index, serializer, true, value, false, failureHandler);
    }

    /**
     * 元素缺失时返回 null, 元素存在但值错误时仍按失败处理.
     */
    public Element<A> optional() {
        return new Element<>(index, serializer, false, null, true, failureHandler);
    }

    /**
     * 元素缺失或元素值错误时, 调用 handler 生成兜底值.
     */
    public Element<A> onFail(Function<? super RuntimeException, ? extends A> handler) {
        return new Element<>(index, serializer, hasDefault, defaultValue, optional, Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 绑定编码时从目标对象读取元素值的 getter.
     */
    public <T> ElementComponent<T, A> forGetter(Function<? super T, ? extends A> getter) {
        return new ElementComponent<>(index, serializer, hasDefault, defaultValue, optional, failureHandler, getter);
    }
}
