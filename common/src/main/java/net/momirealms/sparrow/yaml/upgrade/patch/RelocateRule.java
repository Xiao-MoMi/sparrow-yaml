package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.AbstractYamlNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import org.snakeyaml.engine.v2.comments.CommentLine;

import java.util.ArrayList;
import java.util.List;

/**
 * 将节点从旧路径迁移到新路径的补丁规则.
 */
public class RelocateRule implements Patch {

    /** 原节点路径. */
    private final Route from;
    /** 目标节点路径. */
    private final Route to;

    /**
     * 创建一个节点迁移规则.
     *
     * @param from 原路径
     * @param to 新路径
     */
    public RelocateRule(Route from, Route to) {
        this.from = from;
        this.to = to;
    }

    /**
     * 将本地文档中的节点迁移到目标路径, 并尽量保留原节点注释与表示风格.
     */
    @Override
    public YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context) {
        YamlNode<?> fromNode = localDoc.getNodeOrNull(from);
        if (fromNode != null && localDoc.getNodeOrNull(to) == null) {
            // 记录迁移过程中需要补建的父节点, 后续可从模板同步其注释.
            List<Route> missingParents = new ArrayList<>();
            Route currentParent = to.parent();
            while (currentParent != null && localDoc.getNodeOrNull(currentParent) == null) {
                missingParents.add(0, currentParent);
                currentParent = currentParent.parent();
            }

            Object valueToMove = fromNode.representValue();
            YamlNode<?> newNode = localDoc.setAndGet(to, valueToMove);
            if (newNode != null) {
                fromNode.preserveFlowStyleTo(newNode);
                fromNode.deepCopyCommentsTo(newNode);

                // 如果新节点成为首个子节点, 去掉顶部多余的空白注释行.
                if (newNode instanceof AbstractYamlNode<?> absNode) {
                    boolean isFirstChild = false;
                    Route parentRoute = to.parent();
                    YamlNode<?> parentNode = parentRoute == null ? localDoc : localDoc.getNodeOrNull(parentRoute);

                    if (parentNode instanceof SectionNode sectionNode) {
                        if (!sectionNode.value().isEmpty()) {
                            Object firstKey = sectionNode.value().keySet().iterator().next();
                            Object currentKey = to.getRouteElement(to.length() - 1).key();
                            if (java.util.Objects.equals(sectionNode.adaptKey(firstKey), sectionNode.adaptKey(currentKey))) {
                                isFirstChild = true;
                            }
                        }
                    } else if (parentNode instanceof SequenceNode sequenceNode) {
                        Object currentKey = to.getRouteElement(to.length() - 1).key();
                        if (currentKey instanceof Integer idx && idx == 0) {
                            isFirstChild = true;
                        }
                    }

                    if (isFirstChild) {
                        List<CommentLine> beforeComments = absNode.beforeKeyComments();
                        if (beforeComments != null && !beforeComments.isEmpty()) {
                            List<CommentLine> cleaned = new ArrayList<>(beforeComments);
                            while (!cleaned.isEmpty() && cleaned.get(0).getValue().trim().isEmpty()) {
                                cleaned.remove(0);
                            }
                            absNode.setBeforeKeyComments(cleaned);
                        }
                    }
                }
            }

            // 为新建父节点补充模板中的注释.
            for (Route missingParent : missingParents) {
                YamlNode<?> localParent = localDoc.getNodeOrNull(missingParent);
                YamlNode<?> defParent = defDoc.getNodeOrNull(missingParent);
                if (localParent != null && defParent != null) {
                    defParent.copyCommentsTo(localParent);
                }
            }

            localDoc.removeNode(from);
        }
        return localDoc;
    }

    /**
     * 返回迁移源路径.
     */
    public Route getFrom() {
        return from;
    }

    /**
     * 返回迁移目标路径.
     */
    public Route getTo() {
        return to;
    }

    /**
     * 迁移规则在大多数补丁之前执行.
     */
    @Override
    public int getOrder() {
        return 20;
    }
}
