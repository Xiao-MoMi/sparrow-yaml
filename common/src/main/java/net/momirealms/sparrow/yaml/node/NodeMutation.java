package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.IndexElement;
import net.momirealms.sparrow.yaml.route.element.RouteElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 集中提交节点图写入, 同步维护 AST、wrapper view、ownership 与 alias identity.
 */
@ApiStatus.Internal
public final class NodeMutation {
    private NodeMutation() {
    }

    static YamlNode<?> replace(SectionNode parent, Object key, @Nullable Object value, @Nullable YamlNode<?> metadataSource) {
        Object adaptedKey = parent.adaptKey(key);
        YamlNode<?> existing = parent.mutableValue().get(adaptedKey);
        Node keyNode = parent.root().sparrowYaml().standardRepresenter().represent(adaptedKey);
        // 新子树先在临时文档完成构建和校验
        AbstractYamlNode<?> target = prepareNode(parent.root(), keyNode, adaptedKey, value);
        applyReplacementMetadata(existing, metadataSource, value, target);

        Node previousKeyNode = existing == null ? null : existing.internalKeyNode();
        if (previousKeyNode != null) {
            target.internalKeyNode(previousKeyNode);
        }
        List<AbstractYamlNode<?>> references = parent.root().nodeReferences(parent);
        if (existing != null) {
            existing.parentNode(null);
        }

        target.parent = parent;
        target.key = adaptedKey;
        parent.mutableValue().put(adaptedKey, target);
        parent.syncNodeTuple(adaptedKey, target, true);
        parent.root().attachNode(target);
        syncAliases(parent, references);
        return target;
    }

    static YamlNode<?> replace(SequenceNode parent, int index, @Nullable Object value, @Nullable YamlNode<?> metadataSource) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Sequence index must be non-negative: " + index);
        }
        YamlNode<?> existing = index < parent.size() ? parent.mutableValue().get(index) : null;
        AbstractYamlNode<?> target = prepareNode(parent.root(), null, index, value);
        applyReplacementMetadata(existing, metadataSource, value, target);

        List<AbstractYamlNode<?>> references = parent.root().nodeReferences(parent);
        if (existing != null) {
            existing.parentNode(null);
        }
        org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode = parent.internalSequenceNode();
        List<Node> nodes = sequenceNode.getValue();
        while (parent.mutableValue().size() < index) {
            int placeholderIndex = parent.mutableValue().size();
            ScalarNode placeholder = ScalarNode.emptyNode(parent.root().sparrowYaml());
            placeholder.parent = parent;
            placeholder.key = placeholderIndex;
            parent.mutableValue().add(placeholder);
            nodes.add(placeholder.internalValueNode());
            parent.root().attachNode(placeholder);
        }

        target.parent = parent;
        target.key = index;
        target.keyNode = null;
        if (index == parent.mutableValue().size()) {
            parent.mutableValue().add(target);
            nodes.add(target.internalValueNode());
        } else {
            parent.mutableValue().set(index, target);
            nodes.set(index, target.internalValueNode());
        }
        parent.root().attachNode(target);
        syncAliases(parent, references);
        return target;
    }

    static void replaceSectionContents(SectionNode sectionNode, @NotNull Map<?, ?> value) {
        // 完整副本构建成功后再接管当前 Section 的子节点
        AbstractYamlNode<?> preparedNode = prepareNode(sectionNode.root(), sectionNode.internalKeyNode(), sectionNode.key(), value);
        SectionNode prepared = (SectionNode) preparedNode;
        Map<Object, YamlNode<?>> replacement = new LinkedHashMap<>(prepared.mutableValue());
        List<AbstractYamlNode<?>> references = sectionNode.root().nodeReferences(sectionNode);

        for (YamlNode<?> child : sectionNode.mutableValue().values()) {
            child.parentNode(null);
        }
        sectionNode.mutableValue().clear();
        for (Map.Entry<Object, YamlNode<?>> entry : replacement.entrySet()) {
            AbstractYamlNode<?> child = asAbstract(entry.getValue());
            child.parent = sectionNode;
            sectionNode.mutableValue().put(entry.getKey(), child);
        }
        sectionNode.rebuildTupleState();
        for (YamlNode<?> child : sectionNode.mutableValue().values()) {
            sectionNode.root().attachNode(asAbstract(child));
        }
        syncAliases(sectionNode, references);
    }

    static void replaceSequenceContents(SequenceNode sequenceNode, @NotNull List<?> value) {
        // 完整副本构建成功后再接管当前 Sequence 的元素
        AbstractYamlNode<?> preparedNode = prepareNode(sequenceNode.root(), sequenceNode.internalKeyNode(), sequenceNode.key(), value);
        SequenceNode prepared = (SequenceNode) preparedNode;
        List<YamlNode<?>> replacement = new ArrayList<>(prepared.mutableValue());
        List<AbstractYamlNode<?>> references = sequenceNode.root().nodeReferences(sequenceNode);

        for (YamlNode<?> child : sequenceNode.mutableValue()) {
            child.parentNode(null);
        }
        sequenceNode.mutableValue().clear();
        sequenceNode.internalSequenceNode().getValue().clear();
        for (int i = 0; i < replacement.size(); i++) {
            AbstractYamlNode<?> child = asAbstract(replacement.get(i));
            child.parent = sequenceNode;
            child.key = i;
            child.keyNode = null;
            sequenceNode.mutableValue().add(child);
            sequenceNode.internalSequenceNode().getValue().add(child.internalValueNode());
            sequenceNode.root().attachNode(child);
        }
        syncAliases(sequenceNode, references);
    }

    @Nullable
    static YamlNode<?> remove(SectionNode parent, Object key) {
        Object adaptedKey = parent.adaptKey(key);
        YamlNode<?> removed = parent.mutableValue().get(adaptedKey);
        if (removed == null) return null;
        remove(parent, Set.of(adaptedKey));
        return removed;
    }

    static void remove(SectionNode parent, @NotNull Collection<?> keys) {
        Set<Object> adaptedKeys = new HashSet<>();
        for (Object key : keys) {
            adaptedKeys.add(parent.adaptKey(key));
        }
        adaptedKeys.retainAll(parent.mutableValue().keySet());
        if (adaptedKeys.isEmpty()) return;

        List<AbstractYamlNode<?>> references = parent.root().nodeReferences(parent);
        Iterator<Map.Entry<Object, YamlNode<?>>> iterator = parent.mutableValue().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, YamlNode<?>> entry = iterator.next();
            if (adaptedKeys.contains(entry.getKey())) {
                iterator.remove();
                entry.getValue().parentNode(null);
            }
        }
        parent.rebuildTupleState();
        syncAliases(parent, references);
    }

    @Nullable
    static YamlNode<?> remove(SequenceNode parent, int index) {
        if (index < 0 || index >= parent.size()) return null;
        List<AbstractYamlNode<?>> references = parent.root().nodeReferences(parent);
        YamlNode<?> removed = parent.mutableValue().remove(index);
        parent.internalSequenceNode().getValue().remove(index);
        removed.parentNode(null);
        for (int i = index; i < parent.mutableValue().size(); i++) {
            asAbstract(parent.mutableValue().get(i)).key = i;
        }
        syncAliases(parent, references);
        return removed;
    }

    static void reorder(SectionNode parent, @NotNull List<Object> desiredOrder) {
        Map<Object, YamlNode<?>> reordered = new LinkedHashMap<>();
        for (Object key : desiredOrder) {
            Object adaptedKey = parent.adaptKey(key);
            YamlNode<?> child = parent.mutableValue().get(adaptedKey);
            if (child != null) {
                reordered.put(adaptedKey, child);
            }
        }
        for (Map.Entry<Object, YamlNode<?>> entry : parent.mutableValue().entrySet()) {
            reordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (hasSameKeyOrder(parent.mutableValue(), reordered)) return;

        List<AbstractYamlNode<?>> references = parent.root().nodeReferences(parent);
        parent.mutableValue().clear();
        parent.mutableValue().putAll(reordered);
        parent.rebuildTupleState();
        syncAliases(parent, references);
    }

    static void replaceScalar(ScalarNode scalarNode, @Nullable Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            throw new IllegalArgumentException("不能给 ScalarNode 设置 Map 或 List 的值!");
        }
        org.snakeyaml.engine.v2.nodes.ScalarNode represented =
                (org.snakeyaml.engine.v2.nodes.ScalarNode) scalarNode.root().sparrowYaml().standardRepresenter().represent(value);
        represented.setBlockComments(copyComments(scalarNode.beforeValueComments));
        represented.setInLineComments(copyComments(scalarNode.inlineValueComments));
        represented.setEndComments(copyComments(scalarNode.afterValueComments));

        YamlDocument root = scalarNode.root();
        List<AbstractYamlNode<?>> references = root.nodeReferences(scalarNode);
        for (int i = 0; i < references.size(); i++) {
            AbstractYamlNode<?> reference = references.get(i);
            if (!(reference instanceof ScalarNode alias)) {
                continue;
            }
            Node previousNode = alias.valueNode;
            alias.value = value;
            alias.valueNode = represented;
            alias.initComments(alias.keyNode, represented);
            root.rebindNode(alias, previousNode);
        }
        for (int i = 0; i < references.size(); i++) {
            AbstractYamlNode<?> reference = references.get(i);
            if (!(reference instanceof ScalarNode alias)) {
                continue;
            }
            if (alias.parent instanceof SectionNode section) {
                section.syncValueNode(alias);
            } else if (alias.parent instanceof SequenceNode sequence) {
                sequence.syncValueNode(alias);
            }
        }
    }

    static void replaceSerialized(YamlNode<?> yamlNode, @Nullable Object value) {
        if (yamlNode instanceof ScalarNode scalarNode && !(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
            replaceScalar(scalarNode, value);
            return;
        }
        if (yamlNode instanceof YamlDocument document) {
            if (!(value instanceof Map<?, ?> map)) {
                String actualType = value == null ? "null" : value.getClass().getName();
                throw new IllegalArgumentException("YamlDocument 根节点只支持 Map 序列化结果, 实际类型: " + actualType);
            }
            replaceRootValue(document, map);
            return;
        }

        ParentNode<?> parent = yamlNode.parentNode();
        if (parent instanceof SectionNode sectionNode) {
            replace(sectionNode, yamlNode.key(), value, yamlNode);
            return;
        }
        if (parent instanceof SequenceNode sequenceNode) {
            replace(sequenceNode, (Integer) yamlNode.key(), value, yamlNode);
            return;
        }
        throw new IllegalStateException("当前 Node 尚未挂载到可替换它的 ParentNode 上!");
    }

    public static void replaceRootValue(@NotNull YamlDocument document, @NotNull Map<?, ?> value) {
        replaceSectionContents(document, value);
    }

    public static void adoptRoot(@NotNull YamlDocument document, @NotNull YamlDocument replacement) {
        SectionNode documentSection = document;
        SectionNode replacementSection = replacement;
        Map<Object, YamlNode<?>> replacementValues = new LinkedHashMap<>(replacementSection.mutableValue());
        for (YamlNode<?> child : documentSection.mutableValue().values()) {
            child.parentNode(null);
        }
        for (YamlNode<?> child : replacementValues.values()) {
            child.parentNode(null);
        }

        // 根 wrapper identity 保持稳定, 内部 AST 和子节点整体接管 staging 结果
        document.valueNode = replacement.valueNode;
        documentSection.mutableValue().clear();
        for (Map.Entry<Object, YamlNode<?>> entry : replacementValues.entrySet()) {
            AbstractYamlNode<?> child = asAbstract(entry.getValue());
            child.parent = document;
            documentSection.mutableValue().put(entry.getKey(), child);
        }
        document.copyTupleStateFrom(replacement);
        document.initComments(null, document.valueNode);
        for (YamlNode<?> child : documentSection.mutableValue().values()) {
            document.attachNode(asAbstract(child));
        }
    }

    @Nullable
    public static YamlNode<?> move(@NotNull YamlDocument document, @NotNull Route from, @NotNull Route to) {
        if (from.length() == 0 || to.length() == 0) {
            throw new IllegalArgumentException("根节点不能作为 move 的源或目标");
        }
        if (from.equals(to)) return document.getNodeOrNull(from);
        if (from.isParentRouteOf(to)) {
            throw new IllegalArgumentException("节点不能移动到自己的后代路径: " + Route.pathName(to));
        }
        YamlNode<?> source = document.getNodeOrNull(from);
        if (source == null || document.getNodeOrNull(to) != null) return null;
        if (targetsSourceGraph(document, source, to)) {
            throw new IllegalArgumentException("节点不能通过 alias 移动到自己的节点图中: " + Route.pathName(to));
        }

        ParentNode<?> sourceParent = source.parentNode();
        if (sourceParent == null) {
            throw new IllegalStateException("待移动节点没有 ParentNode: " + Route.pathName(from));
        }
        ParentNode<?> destinationParent = resolveParent(document, to);
        if (containsNode(source.internalValueNode(), destinationParent.yamlNode().internalValueNode(), new IdentityHashMap<>())) {
            throw new IllegalArgumentException("节点不能移动到自己的节点图中: " + Route.pathName(to));
        }
        ParentNode<?> effectiveDestination = sourceParent.yamlNode().internalValueNode() == destinationParent.yamlNode().internalValueNode()
                ? sourceParent
                : destinationParent;
        MoveTarget moveTarget = prepareMoveTarget(effectiveDestination, to.getLastRouteElement());
        List<AbstractYamlNode<?>> sourceReferences = document.nodeReferences(asAbstract(sourceParent.yamlNode()));
        List<AbstractYamlNode<?>> destinationReferences = effectiveDestination == sourceParent
                ? sourceReferences
                : document.nodeReferences(asAbstract(effectiveDestination.yamlNode()));

        AbstractYamlNode<?> moved = asAbstract(source);
        // 校验和目标父节点准备完成后再从原位置取出节点
        take(sourceParent, moved);
        insertMoved(moveTarget, moved);
        syncAliases(asAbstract(sourceParent.yamlNode()), sourceReferences);
        if (effectiveDestination != sourceParent) {
            syncAliases(asAbstract(effectiveDestination.yamlNode()), destinationReferences);
        }
        return document.getNodeOrNull(to);
    }

    private static ParentNode<?> resolveParent(YamlDocument document, Route route) {
        YamlNode<?> current = document;
        for (int i = 0; i < route.length() - 1; i++) {
            RouteElement<?> element = route.getRouteElement(i);
            RouteElement<?> childElement = route.getRouteElement(i + 1);
            Object containerValue = childElement instanceof IndexElement ? new ArrayList<>() : new LinkedHashMap<>();
            if (current instanceof SequenceNode sequenceNode) {
                if (!(element instanceof IndexElement indexElement)) {
                    throw new UnsupportedOperationException("SequenceNode 的路径元素必须是 IndexElement: " + element);
                }
                YamlNode<?> child = sequenceNode.getNodeOrNull(Route.from(indexElement));
                if (!matchesContainer(child, childElement)) {
                    child = replace(sequenceNode, indexElement.key(), containerValue, child);
                }
                current = child;
            } else if (current instanceof SectionNode sectionNode) {
                YamlNode<?> child = sectionNode.getNodeOrNull(Route.from(element));
                if (!matchesContainer(child, childElement)) {
                    child = replace(sectionNode, element.key(), containerValue, child);
                }
                current = child;
            } else {
                throw new UnsupportedOperationException("路径中的节点不支持子节点: " + current.getClass().getSimpleName());
            }
        }
        return (ParentNode<?>) current;
    }

    private static boolean matchesContainer(@Nullable YamlNode<?> node, RouteElement<?> childElement) {
        return childElement instanceof IndexElement ? node instanceof SequenceNode : node instanceof SectionNode;
    }

    private static boolean targetsSourceGraph(YamlDocument document, YamlNode<?> source, Route target) {
        Route current = target.parent();
        while (current != null) {
            YamlNode<?> existing = document.getNodeOrNull(current);
            if (existing != null) {
                return containsNode(source.internalValueNode(), existing.internalValueNode(), new IdentityHashMap<>());
            }
            current = current.parent();
        }
        return false;
    }

    private static boolean containsNode(Node source, Node target, IdentityHashMap<Node, Boolean> visited) {
        if (source == target) return true;
        if (visited.put(source, Boolean.TRUE) != null) return false;
        if (source instanceof MappingNode mappingNode) {
            for (NodeTuple tuple : mappingNode.getValue()) {
                if (containsNode(tuple.getValueNode(), target, visited)) return true;
            }
        } else if (source instanceof org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode) {
            for (Node child : sequenceNode.getValue()) {
                if (containsNode(child, target, visited)) return true;
            }
        }
        return false;
    }

    private static void take(ParentNode<?> parent, AbstractYamlNode<?> moved) {
        if (parent instanceof SectionNode sectionNode) {
            Object key = sectionNode.adaptKey(moved.key());
            sectionNode.mutableValue().remove(key);
            sectionNode.rebuildTupleState();
        } else if (parent instanceof SequenceNode sequenceNode) {
            int index = (Integer) moved.key();
            sequenceNode.mutableValue().remove(index);
            sequenceNode.internalSequenceNode().getValue().remove(index);
            for (int i = index; i < sequenceNode.mutableValue().size(); i++) {
                asAbstract(sequenceNode.mutableValue().get(i)).key = i;
            }
        }
        moved.parent = null;
    }

    private static MoveTarget prepareMoveTarget(ParentNode<?> parent, RouteElement<?> routeElement) {
        if (parent instanceof SectionNode sectionNode) {
            Object key = sectionNode.adaptKey(routeElement.key());
            Node keyNode = sectionNode.root().sparrowYaml().standardRepresenter().represent(key);
            return new MoveTarget(parent, key, keyNode, -1);
        }
        if (parent instanceof SequenceNode && routeElement instanceof IndexElement indexElement) {
            int index = indexElement.key();
            if (index < 0) {
                throw new IndexOutOfBoundsException("Sequence index must be non-negative: " + index);
            }
            return new MoveTarget(parent, null, null, index);
        }
        throw new UnsupportedOperationException("目标 ParentNode 与 RouteElement 类型不匹配");
    }

    private static void insertMoved(MoveTarget target, AbstractYamlNode<?> moved) {
        if (target.parent() instanceof SectionNode sectionNode) {
            moved.key = target.key();
            moved.parent = sectionNode;
            moved.internalKeyNode(target.keyNode());
            sectionNode.mutableValue().put(target.key(), moved);
            sectionNode.syncNodeTuple(target.key(), moved, true);
        } else if (target.parent() instanceof SequenceNode sequenceNode) {
            while (sequenceNode.mutableValue().size() < target.index()) {
                int placeholderIndex = sequenceNode.mutableValue().size();
                ScalarNode placeholder = ScalarNode.emptyNode(sequenceNode.root().sparrowYaml());
                placeholder.parent = sequenceNode;
                placeholder.key = placeholderIndex;
                sequenceNode.mutableValue().add(placeholder);
                sequenceNode.internalSequenceNode().getValue().add(placeholder.internalValueNode());
                sequenceNode.root().attachNode(placeholder);
            }
            moved.key = target.index();
            moved.parent = sequenceNode;
            moved.keyNode = null;
            sequenceNode.mutableValue().add(moved);
            sequenceNode.internalSequenceNode().getValue().add(moved.internalValueNode());
        }
    }

    private static AbstractYamlNode<?> prepareNode(YamlDocument root, @Nullable Node keyNode, @Nullable Object key, @Nullable Object value) {
        YamlDocument staging = root.sparrowYaml().createDocument();
        AbstractYamlNode<?> prepared = buildNode(staging, keyNode, key, value, new IdentityHashMap<>());
        prepared.parentNode(null);
        return prepared;
    }

    private static AbstractYamlNode<?> buildNode(ParentNode<?> parent, @Nullable Node keyNode, @Nullable Object key, @Nullable Object value, IdentityHashMap<Node, Node> clonedNodes) {
        if (value instanceof YamlNode<?> source) {
            Node clonedNode = cloneNode(source.internalValueNode(), clonedNodes);
            AbstractYamlNode<?> target = wrapNode(parent, keyNode, clonedNode, key);
            copyKeyComments(source, target);
            return target;
        }
        if (value instanceof Map<?, ?> map) {
            MappingNode mappingNode = (MappingNode) parent.yamlNode().root().sparrowYaml().standardRepresenter().represent(new LinkedHashMap<>());
            SectionNode sectionNode = new SectionNode(keyNode, mappingNode);
            sectionNode.key = key;
            sectionNode.parentNode(parent);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object adaptedKey = sectionNode.adaptKey(entry.getKey());
                Node childKeyNode = sectionNode.root().sparrowYaml().standardRepresenter().represent(adaptedKey);
                AbstractYamlNode<?> child = buildNode(sectionNode, childKeyNode, adaptedKey, entry.getValue(), clonedNodes);
                sectionNode.appendPreparedChild(adaptedKey, child);
            }
            return sectionNode;
        }
        if (value instanceof List<?> list) {
            org.snakeyaml.engine.v2.nodes.SequenceNode internalNode =
                    (org.snakeyaml.engine.v2.nodes.SequenceNode) parent.yamlNode().root().sparrowYaml().standardRepresenter().represent(new ArrayList<>());
            SequenceNode sequenceNode = new SequenceNode(keyNode, internalNode);
            sequenceNode.key = key;
            sequenceNode.parentNode(parent);
            for (int i = 0; i < list.size(); i++) {
                AbstractYamlNode<?> child = buildNode(sequenceNode, null, i, list.get(i), clonedNodes);
                sequenceNode.appendPreparedChild(child);
            }
            return sequenceNode;
        }

        Node represented = parent.yamlNode().root().sparrowYaml().standardRepresenter().represent(value);
        if (represented instanceof org.snakeyaml.engine.v2.nodes.ScalarNode) {
            return new ScalarNode(parent, keyNode, represented, key, value);
        }
        return wrapNode(parent, keyNode, represented, key);
    }

    private static AbstractYamlNode<?> wrapNode(ParentNode<?> parent, @Nullable Node keyNode, Node valueNode, @Nullable Object key) {
        ExtendedConstructor constructor = new ExtendedConstructor(parent.yamlNode().root().sparrowYaml().loadSettings());
        try {
            constructor.constructSingleDocument(Optional.of(valueNode));
            Object value = constructor.getConstructed(valueNode);
            if (value instanceof Map) {
                return new SectionNode(parent, keyNode, (MappingNode) valueNode, key, constructor);
            }
            if (value instanceof List) {
                return new SequenceNode(parent, keyNode, (org.snakeyaml.engine.v2.nodes.SequenceNode) valueNode, key, constructor);
            }
            return new ScalarNode(parent, keyNode, valueNode, key, value);
        } finally {
            constructor.clear();
        }
    }

    private static Node cloneNode(Node source, IdentityHashMap<Node, Node> clonedNodes) {
        // 重复引用映射到同一个新 Node, 复制图内部的 alias 关系随之保留
        Node existing = clonedNodes.get(source);
        if (existing != null) return existing;

        Node target;
        if (source instanceof org.snakeyaml.engine.v2.nodes.ScalarNode scalarNode) {
            target = new org.snakeyaml.engine.v2.nodes.ScalarNode(
                    scalarNode.getTag(),
                    true,
                    scalarNode.getValue(),
                    scalarNode.getScalarStyle(),
                    scalarNode.getStartMark(),
                    scalarNode.getEndMark(),
                    scalarNode.getRawText()
            );
            clonedNodes.put(source, target);
        } else if (source instanceof MappingNode mappingNode) {
            List<NodeTuple> tuples = new ArrayList<>(mappingNode.getValue().size());
            MappingNode copy = new MappingNode(
                    mappingNode.getTag(),
                    true,
                    tuples,
                    mappingNode.getFlowStyle(),
                    mappingNode.getStartMark(),
                    mappingNode.getEndMark()
            );
            clonedNodes.put(source, copy);
            for (NodeTuple tuple : mappingNode.getValue()) {
                tuples.add(new NodeTuple(cloneNode(tuple.getKeyNode(), clonedNodes), cloneNode(tuple.getValueNode(), clonedNodes)));
            }
            copy.setHasMergeTag(mappingNode.hasMergeTag());
            target = copy;
        } else if (source instanceof org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode) {
            List<Node> elements = new ArrayList<>(sequenceNode.getValue().size());
            org.snakeyaml.engine.v2.nodes.SequenceNode copy = new org.snakeyaml.engine.v2.nodes.SequenceNode(
                    sequenceNode.getTag(),
                    true,
                    elements,
                    sequenceNode.getFlowStyle(),
                    sequenceNode.getStartMark(),
                    sequenceNode.getEndMark()
            );
            clonedNodes.put(source, copy);
            for (Node element : sequenceNode.getValue()) {
                elements.add(cloneNode(element, clonedNodes));
            }
            target = copy;
        } else {
            throw new UnsupportedOperationException("不支持复制 SnakeYAML Node: " + source.getClass().getName());
        }
        copyNodeMetadata(source, target);
        return target;
    }

    private static void copyNodeMetadata(Node source, Node target) {
        target.setRecursive(source.isRecursive());
        target.setBlockComments(copyComments(source.getBlockComments()));
        target.setInLineComments(copyComments(source.getInLineComments()));
        target.setEndComments(copyComments(source.getEndComments()));
    }

    private static void applyReplacementMetadata(@Nullable YamlNode<?> existing, @Nullable YamlNode<?> metadataSource, @Nullable Object value, AbstractYamlNode<?> target) {
        if (value instanceof YamlNode<?>) {
            if (existing != null) {
                copyKeyComments(existing, target);
            }
            return;
        }
        YamlNode<?> source = metadataSource != null ? metadataSource : existing;
        if (source != null) {
            source.copyCommentsTo(target);
            source.preserveFlowStyleTo(target);
        }
    }

    private static void copyKeyComments(YamlNode<?> source, YamlNode<?> target) {
        target.setBeforeKeyComments(copyComments(source.beforeKeyComments()));
        target.setInlineKeyComments(copyComments(source.inlineKeyComments()));
        target.setAfterKeyComments(copyComments(source.afterKeyComments()));
    }

    @Nullable
    private static List<CommentLine> copyComments(@Nullable List<CommentLine> comments) {
        return comments == null ? null : new ArrayList<>(comments);
    }

    private static boolean hasSameKeyOrder(Map<Object, YamlNode<?>> first, Map<Object, YamlNode<?>> second) {
        if (first.size() != second.size()) return false;
        Iterator<Object> firstKeys = first.keySet().iterator();
        Iterator<Object> secondKeys = second.keySet().iterator();
        while (firstKeys.hasNext()) {
            if (!Objects.equals(firstKeys.next(), secondKeys.next())) {
                return false;
            }
        }
        return true;
    }

    private static void syncAliases(AbstractYamlNode<?> source, List<AbstractYamlNode<?>> references) {
        // 每个 alias occurrence 保留自己的 parent/key, 子 wrapper 映射共享同一 AST
        if (source instanceof SectionNode sourceSection) {
            for (int i = 0; i < references.size(); i++) {
                AbstractYamlNode<?> reference = references.get(i);
                if (reference == source || !(reference instanceof SectionNode alias)) {
                    continue;
                }
                for (YamlNode<?> child : alias.mutableValue().values()) {
                    child.parentNode(null);
                }
                alias.mutableValue().clear();
                for (Map.Entry<Object, YamlNode<?>> entry : sourceSection.mutableValue().entrySet()) {
                    YamlNode<?> child = entry.getValue();
                    alias.mutableValue().put(entry.getKey(), AbstractYamlNode.aliasOf(child, alias, entry.getKey(), child.internalKeyNode()));
                }
                alias.copyTupleStateFrom(sourceSection);
            }
        } else if (source instanceof SequenceNode sourceSequence) {
            for (int i = 0; i < references.size(); i++) {
                AbstractYamlNode<?> reference = references.get(i);
                if (reference == source || !(reference instanceof SequenceNode alias)) {
                    continue;
                }
                for (YamlNode<?> child : alias.mutableValue()) {
                    child.parentNode(null);
                }
                alias.mutableValue().clear();
                for (int index = 0; index < sourceSequence.mutableValue().size(); index++) {
                    YamlNode<?> child = sourceSequence.mutableValue().get(index);
                    alias.mutableValue().add(AbstractYamlNode.aliasOf(child, alias, index, child.internalKeyNode()));
                }
            }
        }
    }

    private static AbstractYamlNode<?> asAbstract(YamlNode<?> node) {
        if (node instanceof AbstractYamlNode<?> abstractNode) return abstractNode;
        throw new UnsupportedOperationException("不支持节点 Implementation: " + node.getClass().getName());
    }

    private record MoveTarget(ParentNode<?> parent, @Nullable Object key, @Nullable Node keyNode, int index) {
    }
}
