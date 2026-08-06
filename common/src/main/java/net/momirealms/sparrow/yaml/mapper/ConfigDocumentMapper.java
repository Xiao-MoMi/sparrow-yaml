package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    YamlDocument toDocument(T instance, @Nullable YamlDocument existing, SparrowYaml yaml);

    /**
     * 从 YamlDocument 反序列化回 Java 实例
     *
     * @param document YamlDocument 实例
     * @param yaml     SparrowYaml 实例，用于反序列化嵌套复杂对象
     * @return 反序列化后的 Java 实例
     */
    T fromDocument(YamlDocument document, SparrowYaml yaml);

    /**
     * 返回子节点属于动态用户数据的 Section 路由, 例如配置类中 Map 字段对应的路由.
     * 升级时这些路由本身仍参与补齐, 但其子节点不会被模板内容合并或清理.
     *
     * @return 动态 Section 路由列表
     */
    default List<Route> dynamicSectionRoutes() {
        return List.of();
    }
}
