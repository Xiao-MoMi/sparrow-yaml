package net.momirealms.sparrow.yaml.upgrade.version;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.InvalidConfigVersionException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;

/**
 * 默认的配置版本提取器.
 * 从指定路由读取标量节点并解析为整数版本号.
 */
public class FieldVersionExtractor implements VersionExtractor {
    private final Route versionRoute;

    /**
     * 使用默认路径 `config-version` 创建版本提取器.
     */
    public FieldVersionExtractor() {
        this(Route.from("config-version"));
    }

    public FieldVersionExtractor(Route versionRoute) {
        this.versionRoute = versionRoute;
    }

    public FieldVersionExtractor(Object... versionRoute) {
        this(Route.from(versionRoute));
    }

    /**
     * 从文档中提取并解析版本号.
     */
    @Override
    public String extractVersion(YamlDocument doc) throws InvalidConfigVersionException {
        YamlNode<?> node = doc.getNodeOrNull(versionRoute);
        if (node == null) {
            throw new InvalidConfigVersionException("Missing version configuration at route: " + versionRoute);
        }

        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value instanceof String str){
                return str;
            }
            else if (value instanceof Number number) {
                return String.valueOf(number);
            }
            else {
                return value.toString();
            }
        }

        throw new InvalidConfigVersionException("Invalid node type for version at route " + versionRoute + ": expected scalar node.");
    }

    @Override
    public Route versionRoute() {
        return this.versionRoute;
    }
}
