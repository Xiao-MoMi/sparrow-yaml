package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.builder.AlternativesBuilder;
import net.momirealms.sparrow.yaml.serializer.builder.MappingBuilder;
import net.momirealms.sparrow.yaml.serializer.builder.SequenceBuilder;

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
                if (!(node instanceof ScalarNode scalarNode)) {
                    throw new InvalidNodeException(node, String.class);
                }
                Object val = scalarNode.value();
                if (val == null) {
                    throw new InvalidNodeException(node, String.class);
                }
                return val.toString();
            },
            value -> value
    );

    public static final NodeSerializer<Integer> INT = NodeSerializer.createInternal(
            Integer.class,
            node -> {
                Object val = scalarValue(node, Integer.class);
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

    public static final NodeSerializer<UUID> UUID = scalar(
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

    public static final NodeSerializer<Locale> LOCALE = scalar(
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

    public static final NodeSerializer<Date> DATE = scalar(
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

    public static final NodeSerializer<Calendar> CALENDAR = scalar(
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

    public static final NodeSerializer<LocalDate> LOCAL_DATE = scalar(LocalDate.class, parse(LocalDate::parse), LocalDate::toString);
    public static final NodeSerializer<LocalTime> LOCAL_TIME = scalar(LocalTime.class, parse(LocalTime::parse), LocalTime::toString);
    public static final NodeSerializer<LocalDateTime> LOCAL_DATE_TIME = scalar(LocalDateTime.class, parse(LocalDateTime::parse), LocalDateTime::toString);
    public static final NodeSerializer<ZonedDateTime> ZONED_DATE_TIME = scalar(ZonedDateTime.class, parse(ZonedDateTime::parse), ZonedDateTime::toString);
    public static final NodeSerializer<Instant> INSTANT = scalar(Instant.class, parse(Instant::parse), Instant::toString);
    public static final NodeSerializer<Duration> DURATION = scalar(Duration.class, parse(Duration::parse), Duration::toString);
    public static final NodeSerializer<Period> PERIOD = scalar(Period.class, parse(Period::parse), Period::toString);

    private NodeSerializers() {
    }

    /**
     * 创建枚举 serializer, 解码时忽略大小写, 编码时写出 enum.name().
     */
    public static <E extends Enum<E>> NodeSerializer<E> enumCodec(Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        Map<String, E> map = new LinkedHashMap<>(Math.max((int) (constants.length / 0.75f) + 1, 16));
        for (E constant : constants) {
            map.put(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        return scalar(
                enumClass,
                str -> map.get(str.toLowerCase(Locale.ROOT)),
                Enum::name
        );
    }

    /**
     * 创建以字符串作为 YAML 表示的值对象 serializer.
     */
    public static <T> NodeSerializer<T> scalar(Function<String, T> reader, Function<T, String> writer) {
        return scalar(Object.class, reader, writer);
    }

    /**
     * 创建以字符串作为 YAML 表示的值对象 serializer, 并声明目标 Java 类型.
     */
    public static <T> NodeSerializer<T> scalar(Class<?> targetType, Function<String, T> reader, Function<T, String> writer) {
        return NodeSerializer.createInternal(
                targetType,
                node -> {
                    Object raw = scalarValue(node, targetType);
                    String decoded = raw.toString();
                    if (decoded.isEmpty()) {
                        throw new InvalidNodeException(node, targetType);
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
                    try {
                        String encoded = writer.apply(value);
                        if (encoded == null) {
                            throw new InvalidNodeException(null, value.getClass(), targetType);
                        }
                        return encoded;
                    } catch (MissingNodeException | InvalidNodeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new InvalidNodeException(null, value.getClass(), targetType, e);
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
        return new MappingBuilder<>(type);
    }

    /**
     * 创建基于 YAML Sequence 的对象 builder.
     */
    public static <T> SequenceBuilder<T> sequence(Class<T> type) {
        return new SequenceBuilder<>(type);
    }

    /**
     * 为同一个 Java 类型创建有序多写法 builder.
     */
    public static <T> AlternativesBuilder<T> alternatives(Class<T> type) {
        return new AlternativesBuilder<>(type);
    }

    private static Object scalarValue(YamlNode<?> node, Class<?> targetType) {
        if (node == null) {
            throw new InvalidNodeException(null, targetType);
        }
        if (!(node instanceof ScalarNode scalarNode)) {
            throw new InvalidNodeException(node, targetType);
        }
        Object value = scalarNode.value();
        if (value == null) {
            throw new InvalidNodeException(node, targetType);
        }
        return value;
    }

    private static Object objectValue(YamlNode<?> node) {
        if (node == null) {
            throw new InvalidNodeException(null, Object.class);
        }
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value == null) {
                throw new InvalidNodeException(node, Object.class);
            }
            return value;
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
}
