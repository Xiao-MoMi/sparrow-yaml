package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * mapping builder 的字段组件, 负责单个字段的读写.
 */
public final class FieldComponent<T, A> implements NodeSerializerComponent<T, A> {
    private final String name; // YAML 字段名
    private final NodeSerializer<A> serializer; // 字段值 serializer
    private final boolean hasDefault; // 缺失时是否使用固定默认值
    private final A defaultValue; // 缺失字段的固定默认值
    private final boolean optional; // 缺失时是否允许 null
    private final Function<? super RuntimeException, ? extends A> failureHandler; // 兜底函数
    private final Function<? super T, ? extends A> getter; // 编码时读取目标对象字段值

    FieldComponent(
            String name,
            NodeSerializer<A> serializer,
            boolean hasDefault,
            A defaultValue,
            boolean optional,
            Function<? super RuntimeException, ? extends A> failureHandler,
            Function<? super T, ? extends A> getter
    ) {
        this.name = name;
        this.serializer = serializer;
        this.hasDefault = hasDefault;
        this.defaultValue = defaultValue;
        this.optional = optional;
        this.failureHandler = failureHandler;
        this.getter = Objects.requireNonNull(getter, "getter");
    }

    @Override
    public NodeSerializerDecodeResult decode(YamlNode<?> node) {
        if (!(node instanceof SectionNode section)) {
            return NodeSerializerDecodeResult.failed();
        }

        YamlNode<?> child = section.getNodeOrNull(name);
        if (child == null) {
            if (hasDefault) {
                return NodeSerializerDecodeResult.success(defaultValue);
            }
            if (optional) {
                return NodeSerializerDecodeResult.success(null);
            }
            return fallback(new MissingNodeException(name, node, serializer.targetType()));
        }

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
        Map<String, Object> map = (Map<String, Object>) target;
        map.put(name, serializer.serialize(getter.apply(source)));
    }

    private NodeSerializerDecodeResult fallback(RuntimeException failure) {
        return NodeSerializerDecodeResult.fallback(failureHandler, failure);
    }
}
