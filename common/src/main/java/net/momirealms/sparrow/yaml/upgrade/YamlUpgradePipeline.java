package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.YamlUpgradeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
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
     * 根据模板文档升级本地文档.
     *
     * @param localDocument 本地 YAML 文档
     * @param defDocument 模板 YAML 文档
     * @return 升级后的 YAML 文档, 也就是 localDocument .
     */
    public YamlDocument upgrade(YamlDocument localDocument, YamlDocument defDocument) {
        String localVersion = versionExtractor.extractVersion(localDocument);
        String defVersion = versionExtractor.extractVersion(defDocument);
        if (localVersion.equals(defVersion)) return localDocument;

        ResolvedUpgradePlan upgradePlan = this.resolveUpgradePlan(localVersion);
        PatchContext patchContext = new PatchContext();

        // 打版本 Patch
        for (Patch patch : upgradePlan.executablePatches()) {
            try {
                localDocument = patch.apply(defDocument, localDocument, patchContext);
            } catch (PatchValidationException e) {
                throw new YamlUpgradeException(localVersion, defVersion, patch, "Validation failed", e);
            } catch (Exception e) {
                throw new YamlUpgradeException(localVersion, defVersion, patch, "Patch application failed", e);
            }
        }

        // 设置版本号到最新
        Route versionRoute = versionExtractor.versionRoute();
        this.setDocumentVersion(localDocument, versionRoute, defVersion);

        // 合并和清理节点
        IgnoredRouteMatcher ignoredRouteMatcher = upgradePlan.ignoredRouteMatcher();
        this.merge(localDocument, defDocument, Route.empty(), ignoredRouteMatcher);
        if (options.deleteRemovedNodes()) {
            clean(localDocument, defDocument, Route.empty(), ignoredRouteMatcher);
        }

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
    private ResolvedUpgradePlan resolveUpgradePlan(String localVersion) {
        List<Patch> executablePatches = new ArrayList<>();
        IgnoredRouteMatcher.Builder ignoredRouteMatcherBuilder = IgnoredRouteMatcher.builder();
        for (Route route : options.globallyIgnoredRoutes()) {
            ignoredRouteMatcherBuilder.add(route);
        }
        for (VersionPatch versionPatch : versionPatches) {
            if (!versionPatch.predicate().matches(localVersion)) {
                continue;
            }
            executablePatches.addAll(versionPatch.orderedPatches());
        }
        return new ResolvedUpgradePlan(List.copyOf(executablePatches), ignoredRouteMatcherBuilder.build());
    }

    /**
     * 将文档的版本号更新为当前补丁链阶段的目标版本.
     */
    private void setDocumentVersion(YamlDocument document, Route versionRoute, String version) {
        YamlNode<?> versionNode = document.getNodeOrNull(versionRoute);
        if (versionNode instanceof ScalarNode scalarNode) {
            scalarNode.setValue(version);
        } else {
            document.set(versionRoute, version);
        }
    }

    /**
     * 以模板文档为准合并本地节点.
     * 保留本地已有值, 仅补齐缺失节点并按需要同步注释和顺序.
     */
    private void merge(YamlNode<?> localNode, YamlNode<?> defNode, Route currentRoute, IgnoredRouteMatcher ignoredRouteMatcher) {
        if (ignoredRouteMatcher.matches(currentRoute)) return;

        if (defNode instanceof SectionNode defSection && localNode instanceof SectionNode localSection) {
            // 以模板顺序为主, 本地独有键统一在最后按原顺序追加.
            List<Object> defKeys = new ArrayList<>(defSection.value().keySet());
            List<Object> localKeys = new ArrayList<>(localSection.value().keySet());
            List<Object> desiredOrder = new ArrayList<>();
            Set<Object> desiredOrderLookup = new HashSet<>();
            for (Object defKey : defKeys) {
                Route childRoute = Route.addTo(currentRoute, defKey);
                if (ignoredRouteMatcher.matches(childRoute)) {
                    if (localKeys.contains(defKey)) {
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
                    merge(childLocalNode, childDefNode, childRoute, ignoredRouteMatcher);
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

            localSection.reorderKeys(desiredOrder);
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
        } else {
            if (localNode.parentNode() instanceof SectionNode parentSection) {
                copyNodeTo(parentSection, localNode.key(), defNode);
            } else if (localNode.parentNode() instanceof SequenceNode parentSequence) {
                if (localNode.key() instanceof Integer index) {
                    Object extractedValue = defNode.representValue();
                    parentSequence.setSubNode(index, extractedValue);
                    YamlNode<?> newNode = parentSequence.value().get(index);
                    if (newNode != null) {
                        defNode.preserveFlowStyleTo(newNode);
                        defNode.deepCopyCommentsTo(newNode);
                    }
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
                Route childRoute = Route.addTo(currentRoute, key);
                if (ignoredRouteMatcher.matches(childRoute)) continue;
                
                if (!defSection.value().containsKey(key)) {
                    keysToRemove.add(key);
                } else {
                    clean(localSection.value().get(key), defSection.value().get(key), childRoute, ignoredRouteMatcher);
                }
            }
            for (Object key : keysToRemove) {
                localSection.removeSubNode(key);
            }
        }
    }

    /**
     * 将源节点复制到目标 SectionNode 下, 并同步表示风格与注释.
     */
    private void copyNodeTo(SectionNode parent, Object key, YamlNode<?> sourceNode) {
        Object extractedValue = sourceNode.representValue();
        parent.setSubNode(key, extractedValue);
        
        YamlNode<?> newNode = parent.value().get(key);
        if (newNode != null) {
            sourceNode.preserveFlowStyleTo(newNode);
            sourceNode.deepCopyCommentsTo(newNode);
        }
    }
}
