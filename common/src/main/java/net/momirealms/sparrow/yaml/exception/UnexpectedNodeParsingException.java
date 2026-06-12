package net.momirealms.sparrow.yaml.exception;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class UnexpectedNodeParsingException extends NodeParsingException {
    private final Route path;
    private final Class<?> actualType;
    private final Class<?> targetType;

    public UnexpectedNodeParsingException(@Nullable YamlNode<?> node, Class<?> targetType, Throwable cause) {
        this(pathOf(node), actualTypeOf(node), targetType, cause);
    }

    public UnexpectedNodeParsingException(
            @Nullable Route path,
            @Nullable Class<?> actualType,
            Class<?> targetType,
            Throwable cause
    ) {
        super(message(path, actualType, targetType, cause), Objects.requireNonNull(cause, "cause"));
        this.path = path;
        this.actualType = actualType;
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    @Nullable
    public Route path() {
        return path;
    }

    @Nullable
    public Class<?> actualType() {
        return actualType;
    }

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

    private static String message(@Nullable Route path, @Nullable Class<?> actualType, Class<?> targetType, Throwable cause) {
        return "Unexpected YAML parsing failure at path " + Route.pathName(path)
                + ": actual " + TypeUtils.typeName(actualType)
                + ", expected " + TypeUtils.typeName(targetType)
                + ", cause " + cause.getClass().getSimpleName();
    }
}
