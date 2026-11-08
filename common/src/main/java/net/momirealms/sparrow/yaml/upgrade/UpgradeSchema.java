package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * 升级时由配置类型提供的结构信息, 用来区分用户数据, 并判断本地写法能否被目标类型读取.
 */
public interface UpgradeSchema {

    /**
     * 返回子节点属于动态用户数据的 Section 路由, 例如配置类中 Map 字段对应的路由.
     * 这些路由本身仍参与补齐, 但其子节点不会被模板内容合并或清理.
     *
     * @return 动态 Section 路由
     */
    @NotNull
    Collection<Route> dynamicSectionRoutes();

    /**
     * 在本地节点与模板节点形状不同时, 判断本地节点能否被该路由的目标类型读取.
     * 返回 true 时升级保留本地节点, 否则用模板节点替换.
     *
     * @param route 本地节点的完整路由
     * @param localNode 与模板形状不同的本地节点
     * @return 本地节点可以被目标类型读取时返回 true
     */
    boolean accepts(@NotNull Route route, @NotNull YamlNode<?> localNode);

    /**
     * 返回没有动态路由, 也不接受其他写法的结构信息.
     *
     * @return 空结构信息
     */
    @NotNull
    static UpgradeSchema empty() {
        return of(List.of());
    }

    /**
     * 返回只登记动态 Section 路由的结构信息, 形状与模板不同的本地节点仍会被替换.
     *
     * @param dynamicSectionRoutes 子节点属于动态用户数据的 Section 路由
     * @return 结构信息
     */
    @NotNull
    static UpgradeSchema of(@NotNull Collection<Route> dynamicSectionRoutes) {
        return new UpgradeSchema() {
            @NotNull
            @Override
            public Collection<Route> dynamicSectionRoutes() {
                return dynamicSectionRoutes;
            }

            @Override
            public boolean accepts(@NotNull Route route, @NotNull YamlNode<?> localNode) {
                return false;
            }
        };
    }
}