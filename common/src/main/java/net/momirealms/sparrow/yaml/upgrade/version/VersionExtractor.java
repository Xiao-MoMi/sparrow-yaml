package net.momirealms.sparrow.yaml.upgrade.version;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.InvalidConfigVersionException;
import net.momirealms.sparrow.yaml.route.Route;

/**
 * 定义从 YAML 文档中提取配置版本号的接口.
 */
public interface VersionExtractor {

    /**
     * 从 YamlDocument 中提取版本号.
     *
     * @param doc YamlDocument
     * @return 版本号
     * @throws InvalidConfigVersionException 版本号未找到或无效
     */
    String extractVersion(YamlDocument doc) throws InvalidConfigVersionException;

    /**
     * 返回版本号所在路由.
     * 默认使用 `config-version`, 自定义提取器可按需覆盖.
     */
    default Route versionRoute() {
        return Route.from("config-version");
    }
}
