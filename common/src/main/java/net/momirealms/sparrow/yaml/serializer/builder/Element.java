package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 声明 sequence serializer 中的一个下标子节点.
 *
 * @param <A> 暴露给所属 group 的组件值类型
 */
public final class Element<A> {
    private final int index;
    private final NodeSerializer<?> serializer;
    private final NodePresence presence;
    private final Object defaultValue;
    private final Function<? super RuntimeException, ? extends A> failureHandler;

    private Element(
            int index,
            NodeSerializer<?> serializer,
            NodePresence presence,
            Object defaultValue,
            Function<? super RuntimeException, ? extends A> failureHandler
    ) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        this.index = index;
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.defaultValue = defaultValue;
        this.failureHandler = failureHandler;
    }

    public static <A> Element<A> required(int index, NodeSerializer<A> serializer) {
        return new Element<>(index, serializer, NodePresence.REQUIRED, null, null);
    }

    public static <A> Element<Optional<A>> optional(int index, NodeSerializer<A> serializer) {
        return new Element<>(index, serializer, NodePresence.OPTIONAL_EMPTY, null, null);
    }

    public static <A> Element<A> optional(int index, NodeSerializer<A> serializer, A defaultValue) {
        return new Element<>(index, serializer, NodePresence.OPTIONAL_DEFAULT, Objects.requireNonNull(defaultValue, "defaultValue"), null);
    }

    public Element<A> onFail(Function<? super RuntimeException, ? extends A> handler) {
        return new Element<>(index, serializer, presence, defaultValue, Objects.requireNonNull(handler, "handler"));
    }

    public <T> ElementComponent<T, A> forGetter(Function<? super T, ? extends A> getter) {
        return new ElementComponent<>(index, serializer, presence, defaultValue, failureHandler, getter);
    }
}
