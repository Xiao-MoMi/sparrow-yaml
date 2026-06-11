package net.momirealms.sparrow.yaml.exception;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.jetbrains.annotations.Nullable;

/**
 * 表示 serializer 解码时 YAML 值存在, 但无法转换成目标 Java 类型.
 */
public class InvalidNodeException extends NodeParsingException {

    private final Route path; // 错误值在 YAML 文档中的路径
    private final Class<?> actualType; // 当前 YAML 节点或标量值对应的 Java 类型
    private final Class<?> targetType; // serializer 期望得到的 Java 类型

    public InvalidNodeException(@Nullable YamlNode<?> node, Class<?> targetType) {
        this(node, targetType, null);
    }

    public InvalidNodeException(@Nullable YamlNode<?> node, Class<?> targetType, @Nullable Throwable cause) {
        this(pathOf(node), actualTypeOf(node), targetType, cause);
    }

    public InvalidNodeException(@Nullable Route path, @Nullable Class<?> actualType, Class<?> targetType) {
        this(path, actualType, targetType, null);
    }

    public InvalidNodeException(
            @Nullable Route path,
            @Nullable Class<?> actualType,
            Class<?> targetType,
            @Nullable Throwable cause
    ) {
        super(message(path, actualType, targetType), cause);
        this.path = path;
        this.actualType = actualType;
        this.targetType = targetType;
    }

    /**
     * 返回错误值在 YAML 文档中的路径.
     */
    @Nullable
    public Route path() {
        return path;
    }

    /**
     * 返回当前 YAML 节点或标量值对应的 Java 类型.
     */
    @Nullable
    public Class<?> actualType() {
        return actualType;
    }

    /**
     * 返回 serializer 期望得到的 Java 类型.
     */
    public Class<?> targetType() {
        return targetType;
    }

    private static Route pathOf(@Nullable YamlNode<?> node) {
        return node == null ? null : node.route();
    }

    private static Class<?> actualTypeOf(@Nullable YamlNode<?> node) {
        if (node == null) {
            return null;
        }
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            return value == null ? null : value.getClass();
        }
        return node.getClass();
    }

    private static String message(@Nullable Route path, @Nullable Class<?> actualType, Class<?> targetType) {
        return "Invalid YAML value at path " + Route.pathName(path) + ": actual " + TypeUtils.typeName(actualType) + ", expected " + TypeUtils.typeName(targetType);
    }
}
