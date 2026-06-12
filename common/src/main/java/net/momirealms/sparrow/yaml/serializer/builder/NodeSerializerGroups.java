package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.NodeParsingException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 承载 mapping/sequence builder 的 group 类型和最终组合逻辑.
 */
public final class NodeSerializerGroups {

    private NodeSerializerGroups() {
    }

    private abstract static class GroupBase<T> {
        final Class<?> targetType; // group 最终构造出的 Java 类型
        final boolean mapping; // true 表示 mapping, false 表示 sequence
        final List<? extends NodeSerializerComponent<T, ?>> components; // group 内的字段或元素组件

        GroupBase(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            this.targetType = targetType;
            this.mapping = mapping;
            this.components = components;
        }
    }

    public static final class Group1<T, A> extends GroupBase<T> {
        Group1(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(Function<A, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0]));
        }
    }

    public static final class Group2<T, A, B> extends GroupBase<T> {
        Group2(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(BiFunction<A, B, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1]));
        }
    }

    public static final class Group3<T, A, B, C> extends GroupBase<T> {
        Group3(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function3<A, B, C, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2]));
        }
    }

    public static final class Group4<T, A, B, C, D> extends GroupBase<T> {
        Group4(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function4<A, B, C, D, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3]));
        }
    }

    public static final class Group5<T, A, B, C, D, E> extends GroupBase<T> {
        Group5(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function5<A, B, C, D, E, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4]));
        }
    }

    public static final class Group6<T, A, B, C, D, E, F> extends GroupBase<T> {
        Group6(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function6<A, B, C, D, E, F, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5]));
        }
    }

    public static final class Group7<T, A, B, C, D, E, F, G> extends GroupBase<T> {
        Group7(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function7<A, B, C, D, E, F, G, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6]));
        }
    }

    public static final class Group8<T, A, B, C, D, E, F, G, H> extends GroupBase<T> {
        Group8(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function8<A, B, C, D, E, F, G, H, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7]));
        }
    }

    public static final class Group9<T, A, B, C, D, E, F, G, H, I> extends GroupBase<T> {
        Group9(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function9<A, B, C, D, E, F, G, H, I, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8]));
        }
    }

    public static final class Group10<T, A, B, C, D, E, F, G, H, I, J> extends GroupBase<T> {
        Group10(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function10<A, B, C, D, E, F, G, H, I, J, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9]));
        }
    }

    public static final class Group11<T, A, B, C, D, E, F, G, H, I, J, K> extends GroupBase<T> {
        Group11(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function11<A, B, C, D, E, F, G, H, I, J, K, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9], (K) values[10]));
        }
    }

    public static final class Group12<T, A, B, C, D, E, F, G, H, I, J, K, L> extends GroupBase<T> {
        Group12(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function12<A, B, C, D, E, F, G, H, I, J, K, L, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9], (K) values[10], (L) values[11]));
        }
    }

    public static final class Group13<T, A, B, C, D, E, F, G, H, I, J, K, L, M> extends GroupBase<T> {
        Group13(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function13<A, B, C, D, E, F, G, H, I, J, K, L, M, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9], (K) values[10], (L) values[11], (M) values[12]));
        }
    }

    public static final class Group14<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> extends GroupBase<T> {
        Group14(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function14<A, B, C, D, E, F, G, H, I, J, K, L, M, N, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9], (K) values[10], (L) values[11], (M) values[12], (N) values[13]));
        }
    }

    public static final class Group15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> extends GroupBase<T> {
        Group15(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function15<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9], (K) values[10], (L) values[11], (M) values[12], (N) values[13], (O) values[14]));
        }
    }

    public static final class Group16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> extends GroupBase<T> {
        Group16(Class<?> targetType, boolean mapping, List<? extends NodeSerializerComponent<T, ?>> components) {
            super(targetType, mapping, components);
        }

        @SuppressWarnings("unchecked")
        public NodeSerializer<T> apply(NodeSerializerFunctions.Function16<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, T> function) {
            return (NodeSerializer<T>) build(targetType, mapping, components, values -> function.apply((A) values[0], (B) values[1], (C) values[2], (D) values[3], (E) values[4], (F) values[5], (G) values[6], (H) values[7], (I) values[8], (J) values[9], (K) values[10], (L) values[11], (M) values[12], (N) values[13], (O) values[14], (P) values[15]));
        }
    }

    private static NodeSerializer<?> build(
            Class<?> targetType,
            boolean mapping,
            List<? extends NodeSerializerComponent<?, ?>> components,
            Function<Object[], ?> factory
    ) {
        return NodeSerializer.createInternal(
                targetType,
                node -> {
                    if (node == null) {
                        throw new InvalidNodeException(null, targetType);
                    }
                    if (mapping && !(node instanceof SectionNode)) {
                        throw new InvalidNodeException(node, targetType);
                    }
                    if (!mapping && !(node instanceof SequenceNode)) {
                        throw new InvalidNodeException(node, targetType);
                    }

                    Object[] values = new Object[components.size()];
                    for (int i = 0; i < components.size(); i++) {
                        NodeSerializerDecodeResult result = components.get(i).decode(node);
                        if (!result.success()) {
                            throw new InvalidNodeException(node, targetType);
                        }
                        values[i] = result.value();
                    }

                    try {
                        Object created = factory.apply(values);
                        if (created == null) {
                            throw new InvalidNodeException(node, targetType);
                        }
                        return created;
                    } catch (NodeParsingException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        throw NodeSerializer.unexpectedDecodeFailure(node, targetType, e);
                    } catch (LinkageError e) {
                        throw NodeSerializer.unexpectedDecodeFailure(node, targetType, e);
                    }
                },
                value -> {
                    if (!mapping) {
                        return encodeSequence(value, targetType, components);
                    }
                    Object target = new LinkedHashMap<String, Object>(Math.max((int) (components.size() / 0.75f) + 1, 16));
                    for (NodeSerializerComponent<?, ?> component : components) {
                        encodeComponent(component, value, target);
                    }
                    return target;
                }
        );
    }

    private static List<Object> encodeSequence(
            Object source,
            Class<?> targetType,
            List<? extends NodeSerializerComponent<?, ?>> components
    ) {
        int max = -1;
        for (NodeSerializerComponent<?, ?> component : components) {
            if (component instanceof ElementComponent<?, ?> element) {
                max = Math.max(max, element.index());
            }
        }

        List<Object> list = new ArrayList<>(max + 1);
        for (int i = 0; i <= max; i++) {
            list.add(null);
        }

        boolean[] declared = new boolean[max + 1];
        boolean[] canOmit = new boolean[max + 1];
        List<ElementComponent<?, ?>> elementsByIndex = new ArrayList<>(max + 1);
        for (int i = 0; i <= max; i++) {
            elementsByIndex.add(null);
        }
        for (NodeSerializerComponent<?, ?> component : components) {
            if (!(component instanceof ElementComponent<?, ?> element)) {
                throw new InvalidNodeException(null, source.getClass(), targetType);
            }
            int index = element.index();
            if (declared[index]) {
                throw new InvalidNodeException(null, source.getClass(), targetType);
            }
            declared[index] = true;
            canOmit[index] = element.allowsTrailingOmission();
            elementsByIndex.set(index, element);
            Object encoded = encodeElement(element, source);
            if (encoded == null && !canOmit[index]) {
                throw element.invalidEncodingValue();
            }
            list.set(index, encoded);
        }

        for (int i = 0; i <= max; i++) {
            if (!declared[i]) {
                throw new InvalidNodeException(null, source.getClass(), targetType);
            }
        }

        int last = list.size() - 1;
        while (last >= 0 && list.get(last) == null && canOmit[last]) {
            last--;
        }
        for (int i = 0; i <= last; i++) {
            if (list.get(i) == null) {
                ElementComponent<?, ?> element = elementsByIndex.get(i);
                if (element != null) {
                    throw element.invalidEncodingValue();
                }
                throw new InvalidNodeException(null, null, targetType);
            }
        }
        return new ArrayList<>(list.subList(0, last + 1));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object encodeElement(ElementComponent component, Object source) {
        return component.encodeValue(source);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void encodeComponent(NodeSerializerComponent component, Object source, Object target) {
        component.encode(source, target);
    }
}
