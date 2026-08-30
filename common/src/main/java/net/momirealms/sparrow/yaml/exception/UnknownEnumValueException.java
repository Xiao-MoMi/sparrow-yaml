package net.momirealms.sparrow.yaml.exception;

import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * 表示标量值没有匹配到目标枚举常量.
 */
@ApiStatus.Internal
public final class UnknownEnumValueException extends InvalidNodeException {
    public UnknownEnumValueException(@NotNull YamlNode<?> node, @NotNull Class<?> targetType) {
        super(node, targetType);
    }
}
