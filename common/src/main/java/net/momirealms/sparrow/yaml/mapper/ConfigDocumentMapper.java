package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;

/**
 * 核心映射器, 实现配置类与 YamlDocument 的双向转换
 */
public interface ConfigDocumentMapper<T> {

    /**
     * 将 Java 实例序列化为 YamlDocument
     *
     * @param instance Java 实例
     * @param yaml     SparrowYaml 实例，用于反序列化嵌套复杂对象或提供配置
     * @return YamlDocument
     */
    YamlDocument toDocument(T instance, SparrowYaml yaml);

    /**
     * 从 YamlDocument 反序列化回 Java 实例
     *
     * @param document YamlDocument 实例
     * @param yaml     SparrowYaml 实例，用于反序列化嵌套复杂对象
     * @return 反序列化后的 Java 实例
     */
    T fromDocument(YamlDocument document, SparrowYaml yaml);
}
