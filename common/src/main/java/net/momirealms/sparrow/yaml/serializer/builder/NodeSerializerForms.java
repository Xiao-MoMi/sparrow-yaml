package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 承载 forms builder, 用于为同一个 Java 类型声明多个 YAML 根形态.
 */
public final class NodeSerializerForms {

    private NodeSerializerForms() {
    }

    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    public static final class Builder<T> {
        private final Class<T> type; // forms 最终解码出的 Java 类型
        private final Map<String, Branch<T>> branchesById = new LinkedHashMap<>();
        private final Map<Shape, Branch<T>> branchesByShape = new LinkedHashMap<>();
        private String serializeAs;

        private Builder(Class<T> type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        /**
         * 注册 YAML mapping 根形态分支.
         */
        public Builder<T> mapping(
                String id,
                Function<? super MappingBuilder<T>, ? extends NodeSerializer<T>> builder
        ) {
            Objects.requireNonNull(builder, "builder");
            return add(id, Shape.MAPPING, builder.apply(new MappingBuilder<>(type)));
        }

        /**
         * 注册 YAML sequence 根形态分支.
         */
        public Builder<T> sequence(
                String id,
                Function<? super SequenceBuilder<T>, ? extends NodeSerializer<T>> builder
        ) {
            Objects.requireNonNull(builder, "builder");
            return add(id, Shape.SEQUENCE, builder.apply(new SequenceBuilder<>(type)));
        }

        /**
         * 注册 YAML scalar 根形态分支.
         */
        public Builder<T> scalar(String id, NodeSerializer<T> serializer) {
            return add(id, Shape.SCALAR, serializer);
        }

        /**
         * 指定编码时使用的规范形态分支.
         */
        public Builder<T> serializeAs(String id) {
            this.serializeAs = requireId(id);
            return this;
        }

        /**
         * 构建 forms serializer.
         */
        public NodeSerializer<T> build() {
            if (serializeAs == null) {
                throw new IllegalStateException("serializeAs(id) is required");
            }
            Branch<T> canonical = branchesById.get(serializeAs);
            if (canonical == null) {
                throw new IllegalArgumentException("Unknown forms branch id '" + serializeAs + "'");
            }
            Map<Shape, Branch<T>> shapeBranches = Map.copyOf(branchesByShape);
            return NodeSerializer.createInternal(
                    type,
                    node -> {
                        Branch<T> branch = selectBranch(node, shapeBranches);
                        if (branch == null) {
                            throw new InvalidNodeException(node, type);
                        }
                        return branch.serializer().deserialize(node);
                    },
                    value -> canonical.serializer().serialize(value)
            );
        }

        private Builder<T> add(String id, Shape shape, NodeSerializer<T> serializer) {
            String requiredId = requireId(id);
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(serializer, "serializer");
            if (branchesById.containsKey(requiredId)) {
                throw new IllegalArgumentException("Duplicate forms branch id '" + requiredId + "'");
            }
            if (branchesByShape.containsKey(shape)) {
                throw new IllegalArgumentException("Duplicate forms branch shape '" + shape.name().toLowerCase() + "'");
            }
            Branch<T> branch = new Branch<>(requiredId, shape, serializer);
            branchesById.put(requiredId, branch);
            branchesByShape.put(shape, branch);
            return this;
        }
    }

    private static <T> Branch<T> selectBranch(YamlNode<?> node, Map<Shape, Branch<T>> branches) {
        Shape shape = shapeOf(node);
        return shape == null ? null : branches.get(shape);
    }

    private static Shape shapeOf(YamlNode<?> node) {
        if (node instanceof SectionNode) {
            return Shape.MAPPING;
        }
        if (node instanceof SequenceNode) {
            return Shape.SEQUENCE;
        }
        if (node instanceof ScalarNode) {
            return Shape.SCALAR;
        }
        return null;
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return id;
    }

    private enum Shape {
        MAPPING,
        SEQUENCE,
        SCALAR
    }

    private record Branch<T>(String id, Shape shape, NodeSerializer<T> serializer) {
    }
}
