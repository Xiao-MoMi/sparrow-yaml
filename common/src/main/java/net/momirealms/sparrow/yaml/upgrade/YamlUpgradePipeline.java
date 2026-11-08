package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.YamlUpgradeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import net.momirealms.sparrow.yaml.upgrade.patch.Patch;
import net.momirealms.sparrow.yaml.upgrade.patch.PatchContext;
import net.momirealms.sparrow.yaml.exception.PatchValidationException;
import net.momirealms.sparrow.yaml.upgrade.patch.VersionPatch;
import net.momirealms.sparrow.yaml.upgrade.version.VersionExtractor;

import java.util.*;

public class YamlUpgradePipeline {
    private final VersionExtractor versionExtractor;
    private final List<VersionPatch> versionPatches;
    private final MergeOptions options;

    public YamlUpgradePipeline(
            VersionExtractor versionExtractor,
            List<VersionPatch> versionPatches,
            MergeOptions options
    ) {
        this.versionExtractor = versionExtractor;
        this.versionPatches = versionPatches;
        this.options = options;
    }

    public static YamlUpgradeBuilder builder() {
        return new YamlUpgradeBuilder();
    }

    public MergeOptions getOptions() {
        return options;
    }

    public VersionExtractor getVersionExtractor() {
        return versionExtractor;
    }

    /**
     * 判断本地文档是否需要根据模板文档执行升级.
     */
    public boolean needsUpgrade(YamlDocument localDocument, YamlDocument defDocument) {
        String localVersion = versionExtractor.extractVersion(localDocument);
        String targetVersion = versionExtractor.extractTargetVersion(defDocument);
        return !localVersion.equals(targetVersion);
    }

    /**
     * 将当前目标版本写入文档.
     */
    public void writeTargetVersion(YamlDocument document) {
        String targetVersion = versionExtractor.extractTargetVersion(document);
        versionExtractor.writeVersion(document, targetVersion);
    }

    /**
     * 根据模板文档升级本地文档.
     *
     * @param localDocument 本地 YAML 文档
     * @param defDocument 模板 YAML 文档
     * @return 升级后的 YAML 文档, 也就是 localDocument .
     */
    public YamlDocument upgrade(YamlDocument localDocument, YamlDocument defDocument) {
        return this.upgrade(localDocument, defDocument, UpgradeSchema.empty());
    }

    /**
     * 根据模板文档升级本地文档, 并额外登记一组动态 Section 路由.
     * 动态 Section 路由本身仍参与补齐, 但其子节点属于用户数据, 不会被模板内容合并或清理.
     * 集合包含 {@link Route#empty()} 时, 所有非根路由都按动态用户数据处理.
     *
     * @param localDocument 本地 YAML 文档
     * @param defDocument 模板 YAML 文档
     * @param dynamicSectionRoutes 子节点属于动态用户数据的 Section 路由
     * @return 升级后的 YAML 文档, 也就是 localDocument .
     * @deprecated 使用 {@link #upgrade(YamlDocument, YamlDocument, UpgradeSchema)}.
     *             {@link net.momirealms.sparrow.yaml.mapper.YamlMapper} 只调用新重载, 子类重写本方法不会再影响映射器的升级流程.
     */
    @Deprecated(since = "1.0.24")
    public YamlDocument upgrade(YamlDocument localDocument, YamlDocument defDocument, Collection<Route> dynamicSectionRoutes) {
        return this.upgrade(localDocument, defDocument, UpgradeSchema.of(dynamicSectionRoutes));
    }

    /**
     * 根据模板文档升级本地文档, 动态路由和本地写法的兼容判断由配置类型的结构信息提供.
     * 本地节点与模板节点形状不同, 但仍能被目标类型读取时, 保留本地节点.
     *
     * @param localDocument 本地 YAML 文档
     * @param defDocument 模板 YAML 文档
     * @param schema 配置类型提供的结构信息
     * @return 升级后的 YAML 文档, 也就是 localDocument .
     */
    public YamlDocument upgrade(YamlDocument localDocument, YamlDocument defDocument, UpgradeSchema schema) {
        String localVersion = versionExtractor.extractVersion(localDocument);
        String targetVersion = versionExtractor.extractTargetVersion(defDocument);
        if (localVersion.equals(targetVersion)) {
            return localDocument;
        }

        ResolvedUpgradePlan upgradePlan = this.resolveUpgradePlan(localVersion, schema.dynamicSectionRoutes());
        PatchContext patchContext = new PatchContext();

        // 打版本 Patch
        for (Patch patch : upgradePlan.executablePatches()) {
            try {
                localDocument = patch.apply(defDocument, localDocument, patchContext);
            } catch (PatchValidationException e) {
                throw new YamlUpgradeException(localVersion, targetVersion, patch, "Validation failed", e);
            } catch (Exception e) {
                throw new YamlUpgradeException(localVersion, targetVersion, patch, "Patch application failed", e);
            }
        }

        // 合并和清理节点
        IgnoredRouteMatcher ignoredRouteMatcher = upgradePlan.ignoredRouteMatcher();
        this.merge(localDocument, defDocument, Route.empty(), ignoredRouteMatcher, schema);
        if (options.deleteRemovedNodes()) {
            clean(localDocument, defDocument, Route.empty(), ignoredRouteMatcher);
        }

        // 设置版本号到最新
        versionExtractor.writeVersion(localDocument, targetVersion);
        return localDocument;
    }

    /**
     * 文档更新计划
     *
     * @param executablePatches 需要应用的Patches
     * @param ignoredRouteMatcher 需要忽略的节点
     */
    private record ResolvedUpgradePlan(
            List<Patch> executablePatches,
            IgnoredRouteMatcher ignoredRouteMatcher
    ) { }

    /**
     * 解析本次升级命中的补丁, 并编译全局忽略节点匹配器.
     */
    private ResolvedUpgradePlan resolveUpgradePlan(String localVersion, Collection<Route> dynamicSectionRoutes) {
        List<Patch> executablePatches = new ArrayList<>();
        IgnoredRouteMatcher.Builder ignoredRouteMatcherBuilder = IgnoredRouteMatcher.builder();
        for (Route route : options.globallyIgnoredRoutes()) {
            ignoredRouteMatcherBuilder.add(route);
        }
        ignoredRouteMatcherBuilder.addDescendants(dynamicSectionRoutes);
        for (VersionPatch versionPatch : versionPatches) {
            if (!versionPatch.predicate().matches(localVersion)) {
                continue;
            }
            executablePatches.addAll(versionPatch.orderedPatches());
        }
        executablePatches.sort(Comparator.naturalOrder());
        return new ResolvedUpgradePlan(List.copyOf(executablePatches), ignoredRouteMatcherBuilder.build());
    }

    /**
     * 以模板文档为准合并本地节点.
     * 保留本地已有值, 仅补齐缺失节点并按需要同步注释和顺序.
     */
    private void merge(YamlNode<?> localNode, YamlNode<?> defNode, Route currentRoute, IgnoredRouteMatcher ignoredRouteMatcher, UpgradeSchema schema) {
        if (ignoredRouteMatcher.matches(currentRoute)) return;

        if (defNode instanceof SectionNode defSection && localNode instanceof SectionNode localSection) {
            // 以模板顺序为主, 本地独有键统一在最后按原顺序追加.
            List<Object> defKeys = new ArrayList<>(defSection.value().keySet());
            List<Object> localKeys = new ArrayList<>(localSection.value().keySet());
            List<Object> desiredOrder = new ArrayList<>();
            Set<Object> desiredOrderLookup = new HashSet<>();
            for (Object defKey : defKeys) {
                Route childRoute = Route.addTo(currentRoute, new KeyElement(defKey));
                if (ignoredRouteMatcher.matches(childRoute)) {
                    if (localSection.value().containsKey(defKey)) {
                        desiredOrder.add(defKey);
                        desiredOrderLookup.add(defKey);
                    }
                    continue;
                }
                desiredOrder.add(defKey);
                desiredOrderLookup.add(defKey);

                YamlNode<?> childDefNode = defSection.value().get(defKey);
                YamlNode<?> childLocalNode = localSection.value().get(defKey);

                if (childLocalNode == null) {
                    copyNodeTo(localSection, defKey, childDefNode);
                } else {
                    merge(childLocalNode, childDefNode, childRoute, ignoredRouteMatcher, schema);
                    if (options.updateComments()) {
                        childDefNode.copyNonEmptyCommentsTo(childLocalNode);
                    }
                }
            }
            
            // 追加模板中不存在的本地剩余键, 保持它们原有的相对顺序.
            for (Object lk : localKeys) {
                if (desiredOrderLookup.add(lk)) {
                    desiredOrder.add(lk);
                }
            }

            if (!localKeys.equals(desiredOrder)) {
                localSection.reorderKeys(desiredOrder);
            }
        }
        else if (defNode instanceof SequenceNode && localNode instanceof SequenceNode) {
            if (options.updateComments()) {
                defNode.copyNonEmptyCommentsTo(localNode);
            }
        }
        else if (defNode instanceof ScalarNode && localNode instanceof ScalarNode) {
            if (options.updateComments()) {
                defNode.copyNonEmptyCommentsTo(localNode);
            }
        }
        // 形状不同但目标类型能读取本地写法, 保留用户值并只同步注释.
        else if (schema.accepts(currentRoute, localNode)) {
            if (options.updateComments()) {
                defNode.copyNonEmptyCommentsTo(localNode);
            }
        } else {
            if (localNode.parentNode() instanceof SectionNode parentSection) {
                copyNodeTo(parentSection, localNode.key(), defNode);
            } else if (localNode.parentNode() instanceof SequenceNode parentSequence) {
                if (localNode.key() instanceof Integer index) {
                    parentSequence.setSubNode(index, defNode);
                }
            }
        }
    }

    /**
     * 删除模板中已不存在且未被忽略的本地节点.
     */
    private void clean(YamlNode<?> localNode, YamlNode<?> defNode, Route currentRoute, IgnoredRouteMatcher ignoredRouteMatcher) {
        if (ignoredRouteMatcher.matches(currentRoute)) return;

        if (localNode instanceof SectionNode localSection && defNode instanceof SectionNode defSection) {
            List<Object> keysToRemove = new ArrayList<>();
            for (Object key : localSection.value().keySet()) {
                Route childRoute = Route.addTo(currentRoute, new KeyElement(key));
                if (ignoredRouteMatcher.matches(childRoute)) continue;
                
                if (!defSection.value().containsKey(key)) {
                    keysToRemove.add(key);
                } else {
                    clean(localSection.value().get(key), defSection.value().get(key), childRoute, ignoredRouteMatcher);
                }
            }
            localSection.removeSubNodes(keysToRemove);
        }
    }

    /**
     * 将源节点复制到目标 SectionNode 下, 并同步表示风格与注释.
     */
    private void copyNodeTo(SectionNode parent, Object key, YamlNode<?> sourceNode) {
        parent.setSubNode(key, sourceNode);
    }
}
