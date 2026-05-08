package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

public final class NodeSerializers {

    private NodeSerializers() {}

    /**
     * 基础的 Object Codec.
     * 它不做任何转换, 只负责提取底层的真实值或作为 Object 存储.
     */
    public static final NodeSerializer<Object> OBJECT = new NodeSerializer<>() {
        @Override
        public Object deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            return scalarNode.value();
        }

        @Override
        public Object serialize(Object value) {
            return value;
        }
    };

    /**
     * 基础的 String Codec.
     * 它能将绝大部分 Scalar 值转为字符串.
     */
    public static final NodeSerializer<String> STRING = new NodeSerializer<>() {
        @Override
        public String deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            Object val = scalarNode.value();
            return val != null ? val.toString() : null;
        }

        @Override
        public Object serialize(String value) {
            return value;
        }
    };

    /**
     * Integer Codec, 用于双向映射 Integer 类型.
     */
    public static final NodeSerializer<Integer> INT = new NodeSerializer<>() {
        @Override
        public Integer deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            Object val = scalarNode.value();
            if (val == null) return null;
            if (val instanceof Number n) return n.intValue();
            try {
                return Integer.valueOf(val.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Object serialize(Integer value) {
            return value;
        }
    };

    /**
     * Double Codec, 用于双向映射 Double 类型.
     */
    public static final NodeSerializer<Double> DOUBLE = new NodeSerializer<>() {
        @Override
        public Double deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            Object val = scalarNode.value();
            if (val == null) return null;
            if (val instanceof Number n) return n.doubleValue();
            try {
                return Double.valueOf(val.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Object serialize(Double value) {
            return value;
        }
    };

    /**
     * Float Codec, 用于双向映射 Float 类型.
     */
    public static final NodeSerializer<Float> FLOAT = new NodeSerializer<>() {
        @Override
        public Float deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            Object val = scalarNode.value();
            if (val == null) return null;
            if (val instanceof Number n) return n.floatValue();
            try {
                return Float.valueOf(val.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Object serialize(Float value) {
            return value;
        }
    };

    /**
     * Long Codec, 用于双向映射 Long 类型.
     */
    public static final NodeSerializer<Long> LONG = new NodeSerializer<>() {
        @Override
        public Long deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            Object val = scalarNode.value();
            if (val == null) return null;
            if (val instanceof Number n) return n.longValue();
            try {
                return Long.valueOf(val.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Object serialize(Long value) {
            return value;
        }
    };

    /**
     * Boolean Codec, 用于双向映射 Boolean 类型.
     * 支持 true, false, 以及数字(大于0为true, 小于等于0为false).
     */
    public static final NodeSerializer<Boolean> BOOLEAN = new NodeSerializer<>() {
        @Override
        public Boolean deserialize(YamlNode<?> node) {
            if (!(node instanceof ScalarNode scalarNode)) return null;
            Object val = scalarNode.value();
            if (val == null) return null;
            if (val instanceof Boolean b) return b;
            if (val instanceof Number n) return n.doubleValue() > 0;
            String s = val.toString().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "true" -> true;
                case "false" -> false;
                default -> {
                    try {
                        yield Double.parseDouble(s) > 0;
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
            };
        }

        @Override
        public Object serialize(Boolean value) {
            return value;
        }
    };

    /**
     * 枚举类的通用编解码器创建方法.
     * 在反序列化时忽略大小写进行映射, 并在序列化时使用 enum.name() 进行编码.
     *
     * @param enumClass 枚举类的 Class 实例
     * @param <E>       枚举类型
     * @return 适用于该枚举类型的节点序列化器
     */
    public static <E extends Enum<E>> NodeSerializer<E> enumCodec(Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        Map<String, E> map = new HashMap<>(Math.max((int) (constants.length / 0.75f) + 1, 16));
        for (E constant : constants) {
            map.put(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        return stringBacked(
                str -> {
                    return map.get(str.toLowerCase(Locale.ROOT));
                },
                Enum::name
        );
    }

    /**
     * UUID Codec.
     */
    public static final NodeSerializer<java.util.UUID> UUID = stringBacked(
            str -> {
                try {
                    return java.util.UUID.fromString(str);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            },
            java.util.UUID::toString
    );

    /**
     * Locale Codec. 支持 language, language_country, language_country_variant 格式.
     */
    public static final NodeSerializer<Locale> LOCALE = stringBacked(
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

    /**
     * Date Codec. (基于 ISO-8601 格式处理)
     */
    public static final NodeSerializer<Date> DATE = stringBacked(
            str -> {
                try {
                    return Date.from(Instant.parse(str));
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            date -> date == null ? null : DateTimeFormatter.ISO_INSTANT.format(date.toInstant())
    );

    /**
     * Calendar Codec.
     */
    public static final NodeSerializer<Calendar> CALENDAR = stringBacked(
            str -> {
                try {
                    Date date = Date.from(Instant.parse(str));
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    return cal;
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            cal -> cal == null ? null : DateTimeFormatter.ISO_INSTANT.format(cal.toInstant())
    );

    /**
     * LocalDate Codec.
     */
    public static final NodeSerializer<LocalDate> LOCAL_DATE = stringBacked(
            str -> {
                try {
                    return LocalDate.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            LocalDate::toString
    );

    /**
     * LocalTime Codec.
     */
    public static final NodeSerializer<LocalTime> LOCAL_TIME = stringBacked(
            str -> {
                try {
                    return LocalTime.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            LocalTime::toString
    );

    /**
     * LocalDateTime Codec.
     */
    public static final NodeSerializer<LocalDateTime> LOCAL_DATE_TIME = stringBacked(
            str -> {
                try {
                    return LocalDateTime.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            LocalDateTime::toString
    );

    /**
     * ZonedDateTime Codec.
     */
    public static final NodeSerializer<ZonedDateTime> ZONED_DATE_TIME = stringBacked(
            str -> {
                try {
                    return ZonedDateTime.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            ZonedDateTime::toString
    );

    /**
     * Instant Codec.
     */
    public static final NodeSerializer<Instant> INSTANT = stringBacked(
            str -> {
                try {
                    return Instant.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            Instant::toString
    );

    /**
     * Duration Codec.
     */
    public static final NodeSerializer<Duration> DURATION = stringBacked(
            str -> {
                try {
                    return Duration.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            Duration::toString
    );

    /**
     * Period Codec.
     */
    public static final NodeSerializer<Period> PERIOD = stringBacked(
            str -> {
                try {
                    return Period.parse(str);
                } catch (DateTimeParseException e) {
                    return null;
                }
            },
            Period::toString
    );

    private static <T> NodeSerializer<T> stringBacked(Function<String, T> reader, Function<T, String> writer) {
        return new NodeSerializer<>() {
            @Override
            public T deserialize(YamlNode<?> node) {
                String decoded = STRING.deserialize(node);
                if (decoded == null || decoded.isEmpty()) {
                    return null;
                }
                try {
                    return reader.apply(decoded);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            public Object serialize(T value) {
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
        };
    }
}
