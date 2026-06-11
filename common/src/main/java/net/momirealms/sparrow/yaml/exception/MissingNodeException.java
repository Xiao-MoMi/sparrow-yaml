package net.momirealms.sparrow.yaml.exception;

import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.jetbrains.annotations.Nullable;

public class MissingNodeException extends RuntimeException {
    private final Object key; // 缺失的字段名或序列下标
    private final Route path; // 缺失值在 YAML 文档中的完整路径
    private final Class<?> targetType; // 缺失值原本需要解码成的 Java 类型

    public MissingNodeException(Object key, @Nullable YamlNode<?> parentNode, Class<?> targetType) {
        this(key, pathOf(parentNode, key), targetType);
    }

    public MissingNodeException(Object key, @Nullable Route path, Class<?> targetType) {
        super(message(key, path, targetType));
        this.key = key;
        this.path = path;
        this.targetType = targetType;
    }

    /**
     * 返回缺失字段名或序列下标.
     */
    public Object key() {
        return key;
    }

    /**
     * 返回缺失值在 YAML 文档中的完整路径.
     */
    @Nullable
    public Route path() {
        return path;
    }

    /**
     * 返回缺失值原本需要解码成的 Java 类型.
     */
    public Class<?> targetType() {
        return targetType;
    }

    private static Route pathOf(@Nullable YamlNode<?> parentNode, Object key) {
        Route parentPath = parentNode == null ? null : parentNode.route();
        return parentPath == null ? Route.from(key) : parentPath.add(key);
    }

    private static String message(Object key, @Nullable Route path, Class<?> targetType) {
        if (key instanceof Integer index) {
            return "Missing YAML list index " + index + " at path " + Route.pathName(path) + ", expected " + TypeUtils.typeName(targetType);
        }
        return "Missing YAML value '" + key + "' at path " + Route.pathName(path) + ", expected " + TypeUtils.typeName(targetType);
    }
}
