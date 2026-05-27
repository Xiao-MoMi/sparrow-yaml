package net.momirealms.sparrow.yaml.upgrade.version;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.InvalidConfigVersionException;

/**
 * 定义配置版本号的读取和写回策略.
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
     * 从默认文档中提取目标版本.
     */
    default String extractTargetVersion(YamlDocument defDoc) throws InvalidConfigVersionException {
        return extractVersion(defDoc);
    }

    /**
     * 将版本号写入指定文档.
     *
     * @param doc YamlDocument
     * @param version 版本号
     */
    void writeVersion(YamlDocument doc, String version);
}
