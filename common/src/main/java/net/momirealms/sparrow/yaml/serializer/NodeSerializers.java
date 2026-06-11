package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * NodeSerializer 的公开组合入口.
 *
 * <p>调用方应从这里的基础类型和 builder 出发拼装 serializer, 而不是自行实现底层读写逻辑.</p>
 */
public final class NodeSerializers {

    public static final NodeSerializer<Object> OBJECT = NodeSerializer.createInternal(
            Object.class,
            NodeSerializers::objectValue,
            value -> value
    );

    public static final NodeSerializer<Object> SCALAR = NodeSerializer.createInternal(
            Object.class,
            node -> {
                return scalarValue(node, Object.class);
            },
            value -> value
    );

    public static final NodeSerializer<String> STRING = NodeSerializer.createInternal(
            String.class,
            node -> {
                if (node == null) {
                    return null;
                }
                if (!(node instanceof ScalarNode scalarNode)) {
                    throw new InvalidNodeException(node, String.class);
                }
                Object val = scalarNode.value();
                return val != null ? val.toString() : null;
            },
            value -> value
    );

    public static final NodeSerializer<Integer> INT = NodeSerializer.createInternal(
            Integer.class,
            node -> {
                Object val = scalarValue(node, Integer.class);
                if (val == null) {
                    return null;
                }
                if (val instanceof Number number) {
                    return number.intValue();
                }
                try {
                    return Integer.valueOf(val.toString());
                } catch (NumberFormatException e) {
                    throw new InvalidNodeException(node, Integer.class, e);
                }
            },
            value -> value
    );

    public static final NodeSerializer<Double> DOUBLE = NodeSerializer.createInternal(
            Double.class,
            node -> {
                Object val = scalarValue(node, Double.class);
                if (val == null) {
                    return null;
                }
                if (val instanceof Number number) {
                    return number.doubleValue();
                }
                try {
                    return Double.valueOf(val.toString());
                } catch (NumberFormatException e) {
                    throw new InvalidNodeException(node, Double.class, e);
                }
            },
            value -> value
    );

    public static final NodeSerializer<Float> FLOAT = NodeSerializer.createInternal(
            Float.class,
            node -> {
                Object val = scalarValue(node, Float.class);
                if (val == null) {
                    return null;
                }
                if (val instanceof Number number) {
                    return number.floatValue();
                }
                try {
                    return Float.valueOf(val.toString());
                } catch (NumberFormatException e) {
                    throw new InvalidNodeException(node, Float.class, e);
                }
            },
            value -> value
    );

    public static final NodeSerializer<Long> LONG = NodeSerializer.createInternal(
            Long.class,
            node -> {
                Object val = scalarValue(node, Long.class);
                if (val == null) {
                    return null;
                }
                if (val instanceof Number number) {
                    return number.longValue();
                }
                try {
                    return Long.valueOf(val.toString());
                } catch (NumberFormatException e) {
                    throw new InvalidNodeException(node, Long.class, e);
                }
            },
            value -> value
    );

    public static final NodeSerializer<Boolean> BOOLEAN = NodeSerializer.createInternal(
            Boolean.class,
            node -> {
                Object val = scalarValue(node, Boolean.class);
                if (val == null) {
                    return null;
                }
                if (val instanceof Boolean bool) {
                    return bool;
                }
                if (val instanceof Number number) {
                    return number.doubleValue() > 0;
                }
                String text = val.toString().toLowerCase(Locale.ROOT);
                return switch (text) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> {
                        try {
                            yield Double.parseDouble(text) > 0;
                        } catch (NumberFormatException e) {
                            throw new InvalidNodeException(node, Boolean.class, e);
                        }
                    }
                };
            },
            value -> value
    );

    public static final NodeSerializer<UUID> UUID = stringBacked(
            UUID.class,
            str -> {
                try {
                    return java.util.UUID.fromString(str);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            },
            java.util.UUID::toString
    );

    public static final NodeSerializer<Locale> LOCALE = stringBacked(
            Locale.class,
            input -> {
                String[] segments = input.split("_", 3);
                return switch (segments.length) {
                    case 1 -> new Locale(segments[0]);
                    case 2 -> new Locale(segments[0], segments[1]);
                    case 3 -> new Locale(segments[0], segments[1], segments[2]);
                    default -> null;
                };
            },
            Locale::toString
    );

    public static final NodeSerializer<Date> DATE = stringBacked(
            Date.class,
            str -> {
                try {
                    return Date.from(Instant.parse(str));
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            date -> date == null ? null : DateTimeFormatter.ISO_INSTANT.format(date.toInstant())
    );

    public static final NodeSerializer<Calendar> CALENDAR = stringBacked(
            Calendar.class,
            str -> {
                try {
                    Date date = Date.from(Instant.parse(str));
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    return calendar;
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            calendar -> calendar == null ? null : DateTimeFormatter.ISO_INSTANT.format(calendar.toInstant())
    );

    public static final NodeSerializer<LocalDate> LOCAL_DATE = stringBacked(LocalDate.class, parse(LocalDate::parse), LocalDate::toString);
    public static final NodeSerializer<LocalTime> LOCAL_TIME = stringBacked(LocalTime.class, parse(LocalTime::parse), LocalTime::toString);
    public static final NodeSerializer<LocalDateTime> LOCAL_DATE_TIME = stringBacked(LocalDateTime.class, parse(LocalDateTime::parse), LocalDateTime::toString);
    public static final NodeSerializer<ZonedDateTime> ZONED_DATE_TIME = stringBacked(ZonedDateTime.class, parse(ZonedDateTime::parse), ZonedDateTime::toString);
    public static final NodeSerializer<Instant> INSTANT = stringBacked(Instant.class, parse(Instant::parse), Instant::toString);
    public static final NodeSerializer<Duration> DURATION = stringBacked(Duration.class, parse(Duration::parse), Duration::toString);
    public static final NodeSerializer<Period> PERIOD = stringBacked(Period.class, parse(Period::parse), Period::toString);

    private NodeSerializers() {
    }

    /**
     * 创建枚举 serializer, 解码时忽略大小写, 编码时写出 enum.name().
     */
    public static <E extends Enum<E>> NodeSerializer<E> enumCodec(Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass");
        E[] constants = enumClass.getEnumConstants();
        Map<String, E> map = new LinkedHashMap<>(Math.max((int) (constants.length / 0.75f) + 1, 16));
        for (E constant : constants) {
            map.put(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        return stringBacked(
                enumClass,
                str -> map.get(str.toLowerCase(Locale.ROOT)),
                Enum::name
        );
    }

    /**
     * 创建以字符串作为 YAML 表示的值对象 serializer.
     */
    public static <T> NodeSerializer<T> stringBacked(Function<String, T> reader, Function<T, String> writer) {
        return stringBacked(Object.class, reader, writer);
    }

    /**
     * 创建以字符串作为 YAML 表示的值对象 serializer, 并声明目标 Java 类型.
     */
    public static <T> NodeSerializer<T> stringBacked(Class<?> targetType, Function<String, T> reader, Function<T, String> writer) {
        return NodeSerializer.createInternal(
                targetType,
                node -> {
                    Object raw = scalarValue(node, targetType);
                    String decoded = raw == null ? null : raw.toString();
                    if (decoded == null || decoded.isEmpty()) {
                        return null;
                    }
                    try {
                        T result = reader.apply(decoded);
                        if (result == null) {
                            throw new InvalidNodeException(node, targetType);
                        }
                        return result;
                    } catch (MissingNodeException | InvalidNodeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new InvalidNodeException(node, targetType, e);
                    }
                },
                value -> {
                    if (value == null) {
                        return "";
                    }
                    try {
                        String encoded = writer.apply(value);
                        return encoded == null ? "" : encoded;
                    } catch (Exception e) {
                        return "";
                    }
                }
        );
    }

    /**
     * 延迟获取 serializer, 用于递归类型.
     */
    public static <T> NodeSerializer<T> lazy(Supplier<NodeSerializer<T>> supplier) {
        return NodeSerializer.lazy(supplier);
    }

    /**
     * 创建基于 YAML Section 的对象 builder.
     */
    public static <T> MappingBuilder<T> mapping(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return new MappingBuilder<>(type);
    }

    /**
     * 创建基于 YAML Sequence 的对象 builder.
     */
    public static <T> SequenceBuilder<T> sequence(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return new SequenceBuilder<>(type);
    }

    private static Object scalarValue(YamlNode<?> node, Class<?> targetType) {
        if (node == null) {
            return null;
        }
        if (!(node instanceof ScalarNode scalarNode)) {
            throw new InvalidNodeException(node, targetType);
        }
        return scalarNode.value();
    }

    private static Object objectValue(YamlNode<?> node) {
        if (node == null) {
            return null;
        }
        if (node instanceof ScalarNode scalarNode) {
            return scalarNode.value();
        }
        if (node instanceof SequenceNode sequenceNode) {
            List<Object> result = new ArrayList<>(sequenceNode.value().size());
            for (YamlNode<?> child : sequenceNode.value()) {
                result.add(objectValue(child));
            }
            return result;
        }
        if (node instanceof SectionNode sectionNode) {
            Map<Object, Object> result = new LinkedHashMap<>(Math.max((int) (sectionNode.value().size() / 0.75f) + 1, 16));
            for (Map.Entry<Object, YamlNode<?>> entry : sectionNode.value().entrySet()) {
                result.put(entry.getKey(), objectValue(entry.getValue()));
            }
            return result;
        }
        throw new InvalidNodeException(node, Object.class);
    }

    private static <T> Function<String, T> parse(Function<String, T> parser) {
        return value -> {
            try {
                return parser.apply(value);
            } catch (DateTimeParseException e) {
                return null;
            }
        };
    }

    /**
     * mapping builder 中的单个字段声明.
     */
    public static final class Field<A> {
        private final String name; // YAML 字段名
        private final NodeSerializer<A> serializer; // 字段值 serializer
        private final boolean hasDefault; // 缺失时是否有固定默认值
        private final A defaultValue; // 缺失字段的固定默认值
        private final boolean optional; // 缺失时是否允许 null
        private final Function<? super RuntimeException, ? extends A> failureHandler; // 缺失或错误值的兜底函数

        Field(
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

    /**
     * sequence builder 中的单个元素声明.
     */
    public static final class Element<A> {
        private final int index; // YAML 序列下标
        private final NodeSerializer<A> serializer; // 元素值 serializer
        private final boolean hasDefault; // 缺失时是否有固定默认值
        private final A defaultValue; // 缺失元素的固定默认值
        private final boolean optional; // 缺失时是否允许 null
        private final Function<? super RuntimeException, ? extends A> failureHandler; // 缺失或错误值的兜底函数

        Element(
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

    /**
     * mapping builder 的字段组件, 负责单个字段的读写.
     */
    public static final class FieldComponent<T, A> implements NodeSerializerComponent<T, A> {
        private final String name; // YAML 字段名
        private final NodeSerializer<A> serializer; // 字段值 serializer
        private final boolean hasDefault; // 缺失时是否使用固定默认值
        private final A defaultValue; // 缺失字段的固定默认值
        private final boolean optional; // 缺失时是否允许 null
        private final Function<? super RuntimeException, ? extends A> failureHandler; // 兜底函数
        private final Function<? super T, ? extends A> getter; // 编码时读取目标对象字段值

        private FieldComponent(
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
            return NodeSerializers.fallback(failureHandler, failure);
        }
    }

    /**
     * sequence builder 的元素组件, 负责单个下标的读写.
     */
    public static final class ElementComponent<T, A> implements NodeSerializerComponent<T, A> {
        private final int index; // YAML 序列下标
        private final NodeSerializer<A> serializer; // 元素值 serializer
        private final boolean hasDefault; // 缺失时是否使用固定默认值
        private final A defaultValue; // 缺失元素的固定默认值
        private final boolean optional; // 缺失时是否允许 null
        private final Function<? super RuntimeException, ? extends A> failureHandler; // 兜底函数
        private final Function<? super T, ? extends A> getter; // 编码时读取目标对象元素值

        private ElementComponent(
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
            return NodeSerializers.fallback(failureHandler, failure);
        }
    }

    private static <A> NodeSerializerDecodeResult fallback(
            Function<? super RuntimeException, ? extends A> failureHandler,
            RuntimeException failure
    ) {
        if (failureHandler == null) {
            throw failure;
        }
        try {
            return NodeSerializerDecodeResult.success(failureHandler.apply(failure));
        } catch (Exception e) {
            return NodeSerializerDecodeResult.failed();
        }
    }

    /**
     * mapping builder, 通过字段 group 拼装对象 serializer.
     */
    public static final class MappingBuilder<T> {
        private final Class<T> type; // group 最终构造出的 Java 类型

        private MappingBuilder(Class<T> type) {
            this.type = type;
        }

        public <A> NodeSerializerGroups.Group1<T, A> group(FieldComponent<T, A> a) { return new NodeSerializerGroups.Group1<>(type, true, List.of(a)); }
        public <A, B> NodeSerializerGroups.Group2<T, A, B> group(FieldComponent<T, A> a, FieldComponent<T, B> b) { return new NodeSerializerGroups.Group2<>(type, true, List.of(a, b)); }
        public <A, B, C> NodeSerializerGroups.Group3<T, A, B, C> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c) { return new NodeSerializerGroups.Group3<>(type, true, List.of(a, b, c)); }
        public <A, B, C, D> NodeSerializerGroups.Group4<T, A, B, C, D> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d) { return new NodeSerializerGroups.Group4<>(type, true, List.of(a, b, c, d)); }
        public <A, B, C, D, E> NodeSerializerGroups.Group5<T, A, B, C, D, E> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e) { return new NodeSerializerGroups.Group5<>(type, true, List.of(a, b, c, d, e)); }
        public <A, B, C, D, E, F> NodeSerializerGroups.Group6<T, A, B, C, D, E, F> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f) { return new NodeSerializerGroups.Group6<>(type, true, List.of(a, b, c, d, e, f)); }
        public <A, B, C, D, E, F, G> NodeSerializerGroups.Group7<T, A, B, C, D, E, F, G> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g) { return new NodeSerializerGroups.Group7<>(type, true, List.of(a, b, c, d, e, f, g)); }
        public <A, B, C, D, E, F, G, H> NodeSerializerGroups.Group8<T, A, B, C, D, E, F, G, H> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h) { return new NodeSerializerGroups.Group8<>(type, true, List.of(a, b, c, d, e, f, g, h)); }
        public <A, B, C, D, E, F, G, H, I> NodeSerializerGroups.Group9<T, A, B, C, D, E, F, G, H, I> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i) { return new NodeSerializerGroups.Group9<>(type, true, List.of(a, b, c, d, e, f, g, h, i)); }
        public <A, B, C, D, E, F, G, H, I, J> NodeSerializerGroups.Group10<T, A, B, C, D, E, F, G, H, I, J> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j) { return new NodeSerializerGroups.Group10<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j)); }
        public <A, B, C, D, E, F, G, H, I, J, K> NodeSerializerGroups.Group11<T, A, B, C, D, E, F, G, H, I, J, K> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j, FieldComponent<T, K> k) { return new NodeSerializerGroups.Group11<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j, k)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L> NodeSerializerGroups.Group12<T, A, B, C, D, E, F, G, H, I, J, K, L> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j, FieldComponent<T, K> k, FieldComponent<T, L> l) { return new NodeSerializerGroups.Group12<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j, k, l)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M> NodeSerializerGroups.Group13<T, A, B, C, D, E, F, G, H, I, J, K, L, M> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j, FieldComponent<T, K> k, FieldComponent<T, L> l, FieldComponent<T, M> m) { return new NodeSerializerGroups.Group13<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N> NodeSerializerGroups.Group14<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j, FieldComponent<T, K> k, FieldComponent<T, L> l, FieldComponent<T, M> m, FieldComponent<T, N> n) { return new NodeSerializerGroups.Group14<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> NodeSerializerGroups.Group15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j, FieldComponent<T, K> k, FieldComponent<T, L> l, FieldComponent<T, M> m, FieldComponent<T, N> n, FieldComponent<T, O> o) { return new NodeSerializerGroups.Group15<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> NodeSerializerGroups.Group16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> group(FieldComponent<T, A> a, FieldComponent<T, B> b, FieldComponent<T, C> c, FieldComponent<T, D> d, FieldComponent<T, E> e, FieldComponent<T, F> f, FieldComponent<T, G> g, FieldComponent<T, H> h, FieldComponent<T, I> i, FieldComponent<T, J> j, FieldComponent<T, K> k, FieldComponent<T, L> l, FieldComponent<T, M> m, FieldComponent<T, N> n, FieldComponent<T, O> o, FieldComponent<T, P> p) { return new NodeSerializerGroups.Group16<>(type, true, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)); }
    }

    /**
     * sequence builder, 通过元素 group 拼装对象 serializer.
     */
    public static final class SequenceBuilder<T> {
        private final Class<T> type; // group 最终构造出的 Java 类型

        private SequenceBuilder(Class<T> type) {
            this.type = type;
        }

        public <A> NodeSerializerGroups.Group1<T, A> group(ElementComponent<T, A> a) { return new NodeSerializerGroups.Group1<>(type, false, List.of(a)); }
        public <A, B> NodeSerializerGroups.Group2<T, A, B> group(ElementComponent<T, A> a, ElementComponent<T, B> b) { return new NodeSerializerGroups.Group2<>(type, false, List.of(a, b)); }
        public <A, B, C> NodeSerializerGroups.Group3<T, A, B, C> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c) { return new NodeSerializerGroups.Group3<>(type, false, List.of(a, b, c)); }
        public <A, B, C, D> NodeSerializerGroups.Group4<T, A, B, C, D> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d) { return new NodeSerializerGroups.Group4<>(type, false, List.of(a, b, c, d)); }
        public <A, B, C, D, E> NodeSerializerGroups.Group5<T, A, B, C, D, E> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e) { return new NodeSerializerGroups.Group5<>(type, false, List.of(a, b, c, d, e)); }
        public <A, B, C, D, E, F> NodeSerializerGroups.Group6<T, A, B, C, D, E, F> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f) { return new NodeSerializerGroups.Group6<>(type, false, List.of(a, b, c, d, e, f)); }
        public <A, B, C, D, E, F, G> NodeSerializerGroups.Group7<T, A, B, C, D, E, F, G> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g) { return new NodeSerializerGroups.Group7<>(type, false, List.of(a, b, c, d, e, f, g)); }
        public <A, B, C, D, E, F, G, H> NodeSerializerGroups.Group8<T, A, B, C, D, E, F, G, H> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h) { return new NodeSerializerGroups.Group8<>(type, false, List.of(a, b, c, d, e, f, g, h)); }
        public <A, B, C, D, E, F, G, H, I> NodeSerializerGroups.Group9<T, A, B, C, D, E, F, G, H, I> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i) { return new NodeSerializerGroups.Group9<>(type, false, List.of(a, b, c, d, e, f, g, h, i)); }
        public <A, B, C, D, E, F, G, H, I, J> NodeSerializerGroups.Group10<T, A, B, C, D, E, F, G, H, I, J> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j) { return new NodeSerializerGroups.Group10<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j)); }
        public <A, B, C, D, E, F, G, H, I, J, K> NodeSerializerGroups.Group11<T, A, B, C, D, E, F, G, H, I, J, K> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j, ElementComponent<T, K> k) { return new NodeSerializerGroups.Group11<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j, k)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L> NodeSerializerGroups.Group12<T, A, B, C, D, E, F, G, H, I, J, K, L> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j, ElementComponent<T, K> k, ElementComponent<T, L> l) { return new NodeSerializerGroups.Group12<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j, k, l)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M> NodeSerializerGroups.Group13<T, A, B, C, D, E, F, G, H, I, J, K, L, M> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j, ElementComponent<T, K> k, ElementComponent<T, L> l, ElementComponent<T, M> m) { return new NodeSerializerGroups.Group13<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N> NodeSerializerGroups.Group14<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j, ElementComponent<T, K> k, ElementComponent<T, L> l, ElementComponent<T, M> m, ElementComponent<T, N> n) { return new NodeSerializerGroups.Group14<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> NodeSerializerGroups.Group15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j, ElementComponent<T, K> k, ElementComponent<T, L> l, ElementComponent<T, M> m, ElementComponent<T, N> n, ElementComponent<T, O> o) { return new NodeSerializerGroups.Group15<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)); }
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> NodeSerializerGroups.Group16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> group(ElementComponent<T, A> a, ElementComponent<T, B> b, ElementComponent<T, C> c, ElementComponent<T, D> d, ElementComponent<T, E> e, ElementComponent<T, F> f, ElementComponent<T, G> g, ElementComponent<T, H> h, ElementComponent<T, I> i, ElementComponent<T, J> j, ElementComponent<T, K> k, ElementComponent<T, L> l, ElementComponent<T, M> m, ElementComponent<T, N> n, ElementComponent<T, O> o, ElementComponent<T, P> p) { return new NodeSerializerGroups.Group16<>(type, false, List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)); }
    }
}
