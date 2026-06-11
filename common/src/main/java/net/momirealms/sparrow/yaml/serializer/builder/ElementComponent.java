package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * sequence builder 的元素组件, 负责单个下标的读写.
 */
public final class ElementComponent<T, A> implements NodeSerializerComponent<T, A> {
    private final int index; // YAML 序列下标
    private final NodeSerializer<A> serializer; // 元素值 serializer
    private final boolean hasDefault; // 缺失时是否使用固定默认值
    private final A defaultValue; // 缺失元素的固定默认值
    private final boolean optional; // 缺失时是否允许 null
    private final Function<? super RuntimeException, ? extends A> failureHandler; // 兜底函数
    private final Function<? super T, ? extends A> getter; // 编码时读取目标对象元素值

    ElementComponent(
            int index,
            NodeSerializer<A> serializer,
            boolean hasDefault,
            A defaultValue,
            boolean optional,
            Function<? super RuntimeException, ? extends A> failureHandler,
            Function<? super T, ? extends A> getter
    ) {
        this.index = index;
        this.serializer = serializer;
        this.hasDefault = hasDefault;
        this.defaultValue = defaultValue;
        this.optional = optional;
        this.failureHandler = failureHandler;
        this.getter = Objects.requireNonNull(getter, "getter");
    }

    @Override
    public NodeSerializerDecodeResult decode(YamlNode<?> node) {
        if (!(node instanceof SequenceNode sequence)) {
            return NodeSerializerDecodeResult.failed();
        }
        if (index >= sequence.size()) {
            if (hasDefault) {
                return NodeSerializerDecodeResult.success(defaultValue);
            }
            if (optional) {
                return NodeSerializerDecodeResult.success(null);
            }
            return fallback(new MissingNodeException(index, node, serializer.targetType()));
        }

        YamlNode<?> child = sequence.value().get(index);
        A decoded;
        try {
            decoded = serializer.deserialize(child);
        } catch (MissingNodeException | InvalidNodeException e) {
            return fallback(e);
        }
        if (decoded == null) {
            return fallback(new InvalidNodeException(child, serializer.targetType()));
        }
        return NodeSerializerDecodeResult.success(decoded);
    }

    @Override
    public void encode(T source, Object target) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) target;
        list.set(index, serializer.serialize(getter.apply(source)));
    }

    int index() {
        return index;
    }

    private NodeSerializerDecodeResult fallback(RuntimeException failure) {
        return NodeSerializerDecodeResult.fallback(failureHandler, failure);
    }
}
