package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.patch.VersionPatch;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;
import net.momirealms.sparrow.yaml.upgrade.version.VersionExtractor;
import net.momirealms.sparrow.yaml.upgrade.version.VersionMatcher;

import java.util.*;
import java.util.function.Consumer;

/**
 * {@link YamlUpgradePipeline} 的构建器.
 * 用于集中配置版本提取器、版本补丁和合并选项.
 */
public class YamlUpgradeBuilder {
    private VersionExtractor versionExtractor = new FieldVersionExtractor(); // 负责提取配置版本的提取器
    private final List<VersionPatch> versionPatches = new ArrayList<>(); // 已注册的版本补丁集合
    private boolean updateComments = false;
    private boolean deleteRemovedNodes = true;
    private final Set<Route> ignoredRoutes = new HashSet<>();

    // 设置版本提取器
    public YamlUpgradeBuilder versionExtractor(VersionExtractor extractor) {
        this.versionExtractor = Objects.requireNonNull(extractor, "extractor");
        return this;
    }

    // 注册单个版本补丁
    public YamlUpgradeBuilder addPatch(VersionPatch versionPatch) {
        this.versionPatches.add(versionPatch);
        return this;
    }

    // 注册一个版本补丁
    public YamlUpgradeBuilder addPatch(VersionMatcher matcher, Consumer<VersionPatch.Builder> consumer) {
        VersionPatch.Builder builder = new VersionPatch.Builder(matcher);
        consumer.accept(builder);
        return addPatch(builder.build());
    }

    // 批量注册版本补丁
    public YamlUpgradeBuilder addPatches(List<VersionPatch> versionPatches) {
        for (VersionPatch versionPatch : versionPatches) {
            addPatch(versionPatch);
        }
        return this;
    }

    // 控制合并阶段是否同步模板注释
    public YamlUpgradeBuilder updateComments(boolean updateComments) {
        this.updateComments = updateComments;
        return this;
    }

    // 控制清理阶段是否删除模板中已移除的本地节点
    public YamlUpgradeBuilder deleteRemovedNodes(boolean deleteRemovedNodes) {
        this.deleteRemovedNodes = deleteRemovedNodes;
        return this;
    }

    // 添加一条非空忽略路由
    public YamlUpgradeBuilder addIgnoredRoute(Object... route) {
        this.ignoredRoutes.add(Route.from(route));
        return this;
    }

    /**
     * 添加一条不参与自动合并和清理的路由, {@link Route#empty()} 表示整个文档.
     * 显式版本补丁和版本字段更新不受影响.
     *
     * @param route 忽略路由
     * @return 此构建器
     */
    public YamlUpgradeBuilder addIgnoredRoute(Route route) {
        this.ignoredRoutes.add(route);
        return this;
    }

    /**
     * 批量添加不参与自动合并和清理的路由.
     * 集合中的 {@link Route#empty()} 表示整个文档, 显式版本补丁和版本字段更新不受影响.
     *
     * @param routes 忽略路由
     * @return 此构建器
     */
    public YamlUpgradeBuilder addIgnoredRoutes(Collection<Route> routes) {
        this.ignoredRoutes.addAll(routes);
        return this;
    }

    // 构建升级流水线实例
    public YamlUpgradePipeline build() {
        MergeOptions options = new MergeOptions(
                updateComments,
                deleteRemovedNodes,
                List.copyOf(ignoredRoutes)
        );
        return new YamlUpgradePipeline(versionExtractor, List.copyOf(versionPatches), options);
    }
}
