package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 声明 mapping serializer 中的一个命名子节点.
 *
 * @param <A> 暴露给所属 group 的组件值类型
 */
public final class Field<A> {
    private final String name;
    private final NodeSerializer<?> serializer;
    private final NodePresence presence;
    private final Object defaultValue;
    private final Function<? super RuntimeException, ? extends A> failureHandler;

    private Field(
            String name,
            NodeSerializer<?> serializer,
            NodePresence presence,
            Object defaultValue,
            Function<? super RuntimeException, ? extends A> failureHandler
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.defaultValue = defaultValue;
        this.failureHandler = failureHandler;
    }

    public static <A> Field<A> required(String name, NodeSerializer<A> serializer) {
        return new Field<>(name, serializer, NodePresence.REQUIRED, null, null);
    }

    public static <A> Field<Optional<A>> optional(String name, NodeSerializer<A> serializer) {
        return new Field<>(name, serializer, NodePresence.OPTIONAL_EMPTY, null, null);
    }

    public static <A> Field<A> optional(String name, NodeSerializer<A> serializer, A defaultValue) {
        return new Field<>(name, serializer, NodePresence.OPTIONAL_DEFAULT, Objects.requireNonNull(defaultValue, "defaultValue"), null);
    }

    public Field<A> onFail(Function<? super RuntimeException, ? extends A> handler) {
        return new Field<>(name, serializer, presence, defaultValue, Objects.requireNonNull(handler, "handler"));
    }

    public <T> FieldComponent<T, A> forGetter(Function<? super T, ? extends A> getter) {
        return new FieldComponent<>(name, serializer, presence, defaultValue, failureHandler, getter);
    }
}
