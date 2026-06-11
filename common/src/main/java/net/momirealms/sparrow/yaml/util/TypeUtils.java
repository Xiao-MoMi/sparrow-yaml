package net.momirealms.sparrow.yaml.util;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TypeUtils {
    private TypeUtils() {}

    /**
     * 获取泛型类型(ParameterizedType)中指定索引位置的实际类型参数.
     *
     * @param type  目标泛型类型
     * @param index 要获取的泛型参数索引
     * @return 指定索引位置的泛型参数类型
     * @throws AutoSerializerException 如果传入类型不是 ParameterizedType 或索引越界
     */
    public static Type parameter(Type type, int index) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new AutoSerializerException("Generic type arguments are required for " + type.getTypeName());
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (index >= arguments.length) {
            throw new AutoSerializerException("Missing generic type argument " + index + " for " + type.getTypeName());
        }
        return arguments[index];
    }

    /**
     * 收集泛型类的类型变量与其实际类型之间的映射关系.
     *
     * @param rawType 原始类(Class)对象
     * @param type    实际的类型信息(可能包含泛型参数)
     * @return 包含类型变量到实际类型的映射, 若没有泛型参数则返回空 Map
     */
    public static Map<TypeVariable<?>, Type> typeVariables(Class<?> rawType, Type type) {
        // 如果不是带泛型的类, 则返回空Map.
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return Map.of();
        }
        /// 获取泛型占位符和具体类型的映射
        TypeVariable<?>[] variables = rawType.getTypeParameters();
        Type[] arguments = parameterizedType.getActualTypeArguments();
        Map<TypeVariable<?>, Type> result = new HashMap<>();
        for (int i = 0; i < variables.length; i++) {
            result.put(variables[i], normalize(arguments[i], result));
        }
        return result;
    }

    /**
     * 规范化给定的类型信息, 尝试解析出其中所有的类型变量.
     *
     * @param type      要规范化的类型
     * @param variables 当前已知的类型变量与实际类型映射集合
     * @return 规范化后的类型. 对于解析后的泛型类型, 会包装为内部的 SimpleParameterizedType
     * @throws AutoSerializerException 如果遇到未绑定的类型变量或不支持的类型格式
     */
    public static Type normalize(@NotNull Type type, Map<TypeVariable<?>, Type> variables) {
        Objects.requireNonNull(type, "type");
        // 如果是泛型类占位符, 如 T/K/E , 就尝试从variables里获取实际类型, 然后递归解析.
        if (type instanceof TypeVariable<?> variable) {
            Type resolved = variables.get(variable);
            if (resolved == null) {
                throw new AutoSerializerException("Unbound type variable: " + variable.getName());
            }
            return normalize(resolved, variables);
        }
        // 如果是带泛型的类型, 例如 "Map<String, T>", 将其递归解析, 然后展平为一个没有泛型占位符的最终结果, 例如 "Map<String, List<Integer>>", 最终用 SimpleParameterizedType 包装.
        if (type instanceof ParameterizedType parameterizedType) {
            Type owner = parameterizedType.getOwnerType() != null ? normalize(parameterizedType.getOwnerType(), variables) : null; // 如果是内部类, 则先递归整它的外部类.
            Type raw = parameterizedType.getRawType();
            Type[] arguments = parameterizedType.getActualTypeArguments(); // 实际类型
            Type[] normalizedArguments = new Type[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                normalizedArguments[i] = normalize(arguments[i], variables);
            }
            return new SimpleParameterizedType(owner, raw, normalizedArguments);
        }
        // 如果是普通类型, 就直接返回普通类型.
        if (type instanceof Class<?>) {
            return type;
        }
        throw new AutoSerializerException("Unsupported type: " + type.getTypeName());
    }

    /**
     * 提取给定类型的原始类Raw Type对象.
     *
     * @param type 需要提取原始类型的 Type 实例
     * @return 提取出的 Class 对象
     * @throws AutoSerializerException 如果无法确定该类型的原始类
     */
    public static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        throw new AutoSerializerException("Cannot determine raw type for " + type.getTypeName());
    }

    /**
     * 获取 Java 基础类型的默认值.
     * 对于对象类型始终返回 null.
     *
     * @param type 目标类的 Class 实例
     * @return 对应的基础类型默认值, 对象引用类型则为 null
     */
    public static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == char.class) return '\0';
        return null;
    }

    /**
     * 获取Java对象的类型名称, 如果存在Node对象则翻译成Map和List.
     */
    public static String typeName(@Nullable Class<?> type) {
        if (type == null) {
            return "null";
        }
        if (type == SectionNode.class) {
            return "Map";
        }
        if (type == SequenceNode.class) {
            return "List";
        }
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        String simpleName = type.getSimpleName();
        return simpleName.isEmpty() ? type.getName() : simpleName;
    }

    /**
     * 参数化类型的简单内部实现, 用于承载规范化后的泛型类型数据.
     */
    private record SimpleParameterizedType(
            @Nullable Type ownerType,
            Type rawType,
            Type[] actualTypeArguments
    ) implements ParameterizedType {
        @Override
        public Type @NotNull [] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public @NotNull Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ParameterizedType that)) return false;
            return Objects.equals(ownerType, that.getOwnerType())
                    && Objects.equals(rawType, that.getRawType())
                    && java.util.Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(actualTypeArguments)
                    ^ Objects.hashCode(ownerType)
                    ^ Objects.hashCode(rawType);
        }

        @Override
        public String getTypeName() {
            StringBuilder builder = new StringBuilder(rawType.getTypeName());
            if (actualTypeArguments.length > 0) {
                builder.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(actualTypeArguments[i].getTypeName());
                }
                builder.append(">");
            }
            return builder.toString();
        }
    }
}
