package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 读写 sequence serializer 中的一个下标子节点.
 */
public final class ElementComponent<T, A> implements NodeSerializerComponent<T, A> {
    private final int index;
    private final NodeSerializer<?> serializer;
    private final NodePresence presence;
    private final Object defaultValue;
    private final Function<? super RuntimeException, ? extends A> failureHandler;
    private final Function<? super T, ? extends A> getter;

    ElementComponent(
            int index,
            NodeSerializer<?> serializer,
            NodePresence presence,
            Object defaultValue,
            Function<? super RuntimeException, ? extends A> failureHandler,
            Function<? super T, ? extends A> getter
    ) {
        this.index = index;
        this.serializer = serializer;
        this.presence = presence;
        this.defaultValue = defaultValue;
        this.failureHandler = failureHandler;
        this.getter = Objects.requireNonNull(getter, "getter");
    }

    @Override
    public NodeSerializerDecodeResult decode(YamlNode<?> node) {
        if (!(node instanceof SequenceNode sequence)) {
            return NodeSerializerDecodeResult.failed();
        }
        if (index >= sequence.size()) {
            return missing(node);
        }

        YamlNode<?> child = sequence.value().get(index);
        if (isMissing(child)) {
            return missing(node);
        }

        Object decoded;
        try {
            decoded = serializer.deserialize(child);
        } catch (MissingNodeException | InvalidNodeException e) {
            return fallback(e);
        }
        if (decoded == null) {
            return fallback(new InvalidNodeException(child, serializer.targetType()));
        }
        if (presence == NodePresence.OPTIONAL_EMPTY) {
            return NodeSerializerDecodeResult.success(Optional.of(decoded));
        }
        return NodeSerializerDecodeResult.success(decoded);
    }

    @Override
    public void encode(T source, Object target) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) target;
        list.set(index, encodeValue(source));
    }

    int index() {
        return index;
    }

    Object encodeValue(T source) {
        return serializeAccessValue(getter.apply(source));
    }

    boolean allowsTrailingOmission() {
        return presence == NodePresence.OPTIONAL_EMPTY;
    }

    InvalidNodeException invalidEncodingValue() {
        return new InvalidNodeException(null, null, serializer.targetType());
    }

    private NodeSerializerDecodeResult missing(YamlNode<?> node) {
        return switch (presence) {
            case OPTIONAL_DEFAULT -> NodeSerializerDecodeResult.success(defaultValue);
            case OPTIONAL_EMPTY -> NodeSerializerDecodeResult.success(Optional.empty());
            case REQUIRED -> fallback(new MissingNodeException(index, node, serializer.targetType()));
        };
    }

    private boolean isMissing(YamlNode<?> node) {
        return node == null || node instanceof ScalarNode scalar && scalar.value() == null;
    }

    private Object serializeAccessValue(Object value) {
        if (presence == NodePresence.OPTIONAL_EMPTY) {
            if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
                return null;
            }
            value = optional.get();
        }
        @SuppressWarnings("unchecked")
        NodeSerializer<Object> valueSerializer = (NodeSerializer<Object>) serializer;
        return valueSerializer.serialize(value);
    }

    private NodeSerializerDecodeResult fallback(RuntimeException failure) {
        return NodeSerializerDecodeResult.fallback(failureHandler, failure);
    }
}
