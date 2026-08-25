package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import net.momirealms.sparrow.yaml.route.element.RouteElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import java.util.*;

public class SectionNode extends AbstractYamlNode<Map<Object, YamlNode<?>>> implements ParentNode<SectionNode> {

    private final Map<Object, Integer> tupleIndexes = new HashMap<>();
    private Set<Object> duplicateTupleKeys;

    public SectionNode(
            @NotNull ParentNode<?> parent,
            @Nullable Node keyNode,
            @NotNull MappingNode valueNode,
            @Nullable Object key,
            @NotNull ExtendedConstructor constructor
    ) {
        super(parent, keyNode, valueNode, key, new LinkedHashMap<>());
        init(keyNode, valueNode, constructor);
    }

    public SectionNode(
            @Nullable Node keyNode,
            @NotNull MappingNode valueNode
    ) {
        super();
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.value = new LinkedHashMap<>();
    }

    public static SectionNode createEmpty(@NotNull YamlDocument root) {
        return createEmpty(root.sparrowYaml());
    }

    public static SectionNode createEmpty(@NotNull SparrowYaml sparrowYaml) {
        MappingNode mappingNode = (MappingNode) sparrowYaml.standardRepresenter().represent(new LinkedHashMap<>());
        return new SectionNode(null, mappingNode);
    }

    // 创建根节点时, 使用的预先构造函数;
    @ApiStatus.Internal
    protected SectionNode() {
        super(new LinkedHashMap<>());
    }

    /**
     * 获取当前 Node 的 Key 的字符串形式;
     * @return Key 的字符串;
     */
    @Nullable
    public String getKeyAsString() {
        Object key = key();
        return key == null ? null : key.toString();
    }

    /**
     * 返回仅包含该 Section 的 Key 的路由.
     * 根 Section 返回 {@link Route#empty()}, 其余节点保留 Key 或 Index 的实际类型.
     * @return 仅包含该 Section 的 Key 的路由
     */
    @NotNull
    public Route getKeyAsRoute() {
        if (this.isRoot()) return Route.empty();
        return Route.from(this.keyRouteElement());
    }

    @Override
    public SectionNode yamlNode() {
        return this;
    }

    @Override
    public void setValue(Map<Object, YamlNode<?>> value) {
        this.value.values().forEach(it -> it.parentNode(null));
        value.values().forEach(it -> it.parentNode(this));
        this.value = value;
    }

    @Override
    public YamlNodeType<?> yamlNodeType() {
        return YamlNodeType.SECTION;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public boolean isSection() {
        return true;
    }

    @Override
    public boolean isSequence() {
        return false;
    }

    @Override
    public boolean isScalar() {
        return false;
    }

    /**
     * 将 Object 转成 Section 节点内部存储的 Map 的 Key;
     * @param key Key
     */
    public Object adaptKey(@NotNull Object key) {
        Objects.requireNonNull(key, "Section 节点不允许存在 null 作为 Key !");
        return root().sparrowYaml().allowObjectKey ? key : key.toString();
    }

    /**
     * 删除当前节点下的指定子节点。
     * @param key 子节点的 Key
     * @return 如果删除了节点则返回该节点，如果不存在则返回 null
     */
    @Nullable
    @Override
    public YamlNode<?> removeSubNode(Object key) {
        Object adaptedKey = this.adaptKey(key);
        YamlNode<?> removed = this.value().get(adaptedKey);
        if (removed == null) {
            return null;
        }
        this.removeSubNodesByAdaptedKeys(Collections.singleton(adaptedKey));
        return removed;
    }

    /**
     * 批量删除当前节点下的指定子节点，只执行一次底层 Tuple 与别名同步。
     *
     * @param keys 子节点的 Key
     */
    @ApiStatus.Internal
    public void removeSubNodes(@NotNull Collection<?> keys) {
        Set<Object> adaptedKeys = new HashSet<>();
        for (Object key : keys) {
            adaptedKeys.add(this.adaptKey(key));
        }
        this.removeSubNodesByAdaptedKeys(adaptedKeys);
    }

    /**
     * 在当前节点下创建一个新的子节点, 子节点的值默认为 null ;
     * 如果当前存在这样的一个子节点, 那么就会覆盖这个子节点;
     * 传入 {@link YamlNode} 时会复制节点值、注释与集合样式, 不改变源节点;
     * @param key 子节点的 Key;
     * @param value 子节点的 Value;
     */
    public <T> void setSubNode(Object key, Object value) {
        this.replaceSubNode(key, value, null);
    }

    void replaceSubNode(Object key, @Nullable Object value, @Nullable YamlNode<?> metadataSource) {
        List<AbstractYamlNode<?>> references = this.root().nodeReferences(this);
        Object adaptedKey = this.adaptKey(key);
        YamlNode<?> targetNode = this.createSubNode(adaptedKey, value);
        if (metadataSource != null) {
            metadataSource.preserveFlowStyleTo(targetNode);
            metadataSource.copyCommentsTo(targetNode);
        }
        YamlNode<?> yamlNode = this.value().get(adaptedKey);
        Node previousKeyNode = yamlNode == null ? null : yamlNode.internalKeyNode();
        if (yamlNode != null) {
            yamlNode.parentNode(null);
        }
        this.value().put(adaptedKey, targetNode);
        this.syncNodeTuple(adaptedKey, targetNode, previousKeyNode, true);
        this.syncAliasValues(references);
    }

    private YamlNode<?> createSubNode(Object key, Object value) {
        if (value instanceof YamlNode<?> existingNode) {
            return AbstractYamlNode.copyOf(existingNode, this, key, null);
        }
        if (value instanceof Map map) {
            SectionNode sectionNode = SectionNode.createEmpty(this.root());
            sectionNode.parentNode(this);
            sectionNode.key(key);
            map.forEach(sectionNode::setSubNode);
            return sectionNode;
        }
        if (value instanceof List list) {
            SequenceNode sequenceNode = SequenceNode.createEmpty(this.root());
            sequenceNode.parentNode(this);
            sequenceNode.key(key);
            for (int i = 0; i < list.size(); i++) {
                sequenceNode.setSubNode(i, list.get(i));
            }
            return sequenceNode;
        }
        Node valueNode = this.root().sparrowYaml().standardRepresenter().represent(value);
        if (valueNode == null || valueNode instanceof org.snakeyaml.engine.v2.nodes.ScalarNode scalarNode && scalarNode.getValue() == null) {
            valueNode = this.root().sparrowYaml().standardRepresenter().represent("null");
        }
        return new net.momirealms.sparrow.yaml.node.ScalarNode(this, null, valueNode, key, value);
    }
    
    public void syncValueNode(YamlNode<?> child) {
        this.syncNodeTuple(child.key(), child, child.internalKeyNode(), false);
    }

    private void syncNodeTuple(Object key, YamlNode<?> targetNode, @Nullable Node expectedKeyNode, boolean collapseDuplicates) {
        Object adaptedKey = this.adaptKey(key);
        MappingNode mappingNode = (MappingNode) this.valueNode;
        List<NodeTuple> tuples = mappingNode.getValue();
        Node newValueNode = targetNode.internalValueNode();
        Integer tupleIndex = this.resolveTupleIndex(adaptedKey, expectedKeyNode, tuples);
        if (tupleIndex == null) {
            Node newKeyNode = this.root().sparrowYaml().standardRepresenter().represent(adaptedKey);
            tuples.add(new NodeTuple(newKeyNode, newValueNode));
            this.registerTupleIndex(adaptedKey, tuples.size() - 1);
            if (targetNode instanceof AbstractYamlNode<?> abstractNode) {
                abstractNode.internalKeyNode(newKeyNode);
            }
            return;
        }

        NodeTuple previousTuple = tuples.get(tupleIndex);
        Node keyNode = previousTuple.getKeyNode();
        if (targetNode instanceof AbstractYamlNode<?> abstractNode) {
            abstractNode.keyNode = keyNode;
            abstractNode.initComments(keyNode, abstractNode.valueNode);
        }
        if (!collapseDuplicates || this.duplicateTupleKeys == null || !this.duplicateTupleKeys.contains(adaptedKey)) {
            tuples.set(tupleIndex, new NodeTuple(keyNode, newValueNode));
            return;
        }
        this.rebuildTupleState();
    }

    @Nullable
    private Integer resolveTupleIndex(Object key, @Nullable Node expectedKeyNode, List<NodeTuple> tuples) {
        Integer tupleIndex = this.tupleIndexes.get(key);
        if (tupleIndex != null && tupleIndex < tuples.size()
                && (expectedKeyNode == null || tuples.get(tupleIndex).getKeyNode() == expectedKeyNode)) {
            return tupleIndex;
        }
        if (expectedKeyNode == null) {
            return null;
        }
        // 底层高级接口可能原地重排 Tuple，索引失配时按原 Key Node identity 惰性校正。
        for (int i = 0; i < tuples.size(); i++) {
            if (tuples.get(i).getKeyNode() == expectedKeyNode) {
                this.tupleIndexes.put(key, i);
                return i;
            }
        }
        return null;
    }

    /**
     * Reorders the child nodes and their corresponding internal NodeTuples based on the provided list of keys.
     * Keys not present in the desiredOrder list will be appended at the end in their original relative order.
     */
    public void reorderKeys(List<Object> desiredOrder) {
        Map<Object, YamlNode<?>> newMap = new LinkedHashMap<>();

        for (Object key : desiredOrder) {
            Object adaptedKey = this.adaptKey(key);
            YamlNode<?> node = this.value.get(adaptedKey);
            if (node != null) {
                newMap.put(adaptedKey, node);
            }
        }

        for (Map.Entry<Object, YamlNode<?>> entry : this.value.entrySet()) {
            newMap.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (hasSameKeyOrder(this.value, newMap)) {
            return;
        }

        List<AbstractYamlNode<?>> references = this.root().nodeReferences(this);
        this.value.clear();
        this.value.putAll(newMap);
        this.rebuildTupleState();
        this.syncAliasValues(references);
    }

    private void removeSubNodesByAdaptedKeys(Set<Object> adaptedKeys) {
        adaptedKeys = new HashSet<>(adaptedKeys);
        adaptedKeys.retainAll(this.value.keySet());
        if (adaptedKeys.isEmpty()) {
            return;
        }

        List<AbstractYamlNode<?>> references = this.root().nodeReferences(this);
        Iterator<Map.Entry<Object, YamlNode<?>>> valueIterator = this.value.entrySet().iterator();
        while (valueIterator.hasNext()) {
            Map.Entry<Object, YamlNode<?>> entry = valueIterator.next();
            if (adaptedKeys.contains(entry.getKey())) {
                valueIterator.remove();
                entry.getValue().parentNode(null);
            }
        }

        this.rebuildTupleState();
        this.syncAliasValues(references);
    }

    private void rebuildTupleState() {
        MappingNode mappingNode = (MappingNode) this.valueNode;
        List<NodeTuple> tuples = new ArrayList<>(this.value.size());
        this.tupleIndexes.clear();
        this.duplicateTupleKeys = null;
        for (Map.Entry<Object, YamlNode<?>> entry : this.value.entrySet()) {
            YamlNode<?> child = entry.getValue();
            Node keyNode = child.internalKeyNode();
            if (keyNode == null) {
                keyNode = this.root().sparrowYaml().standardRepresenter().represent(entry.getKey());
                if (child instanceof AbstractYamlNode<?> abstractNode) {
                    abstractNode.internalKeyNode(keyNode);
                }
            }
            tuples.add(new NodeTuple(keyNode, child.internalValueNode()));
            this.registerTupleIndex(entry.getKey(), tuples.size() - 1);
        }
        mappingNode.setValue(tuples);
    }

    private void registerTupleIndex(Object key, int index) {
        if (this.tupleIndexes.put(key, index) != null) {
            if (this.duplicateTupleKeys == null) {
                this.duplicateTupleKeys = new HashSet<>();
            }
            this.duplicateTupleKeys.add(key);
        }
    }

    @ApiStatus.Internal
    protected void copyTupleStateFrom(SectionNode source) {
        this.tupleIndexes.clear();
        this.tupleIndexes.putAll(source.tupleIndexes);
        this.duplicateTupleKeys = source.duplicateTupleKeys == null ? null : new HashSet<>(source.duplicateTupleKeys);
    }

    private static boolean hasSameKeyOrder(Map<Object, YamlNode<?>> first, Map<Object, YamlNode<?>> second) {
        if (first.size() != second.size()) {
            return false;
        }
        Iterator<Object> firstKeys = first.keySet().iterator();
        Iterator<Object> secondKeys = second.keySet().iterator();
        while (firstKeys.hasNext()) {
            if (!Objects.equals(firstKeys.next(), secondKeys.next())) {
                return false;
            }
        }
        return true;
    }

    private void syncAliasValues(List<AbstractYamlNode<?>> references) {
        for (int i = 0; i < references.size(); i++) {
            AbstractYamlNode<?> reference = references.get(i);
            if (reference == this || !(reference instanceof SectionNode alias)) {
                continue;
            }
            for (YamlNode<?> child : alias.value().values()) {
                child.parentNode(null);
            }
            Map<Object, YamlNode<?>> values = new LinkedHashMap<>();
            for (Map.Entry<Object, YamlNode<?>> entry : this.value().entrySet()) {
                YamlNode<?> child = entry.getValue();
                values.put(entry.getKey(), AbstractYamlNode.aliasOf(child, alias, entry.getKey(), child.internalKeyNode()));
            }
            alias.value = values;
            alias.copyTupleStateFrom(this);
        }
    }

    /**
     * 获取当前节点的子路由
     * @param key 目标路由节点
     * @return 新的路由对象
     */
    public Route getSubRoute(@NotNull Object key) {
        Object routeKey = key instanceof RouteElement<?> ? key : new KeyElement(key);
        return Route.addTo(this.route(), routeKey);
    }

    /**
     * 递归获取当前 Section 的普通 Java 值, mapping key 保留实际类型.
     *
     * @return 按 YAML 顺序排列的 Map
     */
    @NotNull
    public Map<Object, Object> getValues() {
        return NodeValueProjection.sectionValues(this);
    }

    /**
     * 初始化当前Section, 将当前Section的子节点解析并添加到 value 中;
     * @param keyNode 当前 Section 的 KeyNode
     * @param valueNode 当前 Section 的 ValueNode
     * @param constructor 构造器用于解析根节点下的所有节点, 将节点解析成 JavaBean
     */
    protected void init(@Nullable Node keyNode, @NotNull MappingNode valueNode, ExtendedConstructor constructor) {
        // 初始化注释
        super.initComments(keyNode, valueNode);
        this.tupleIndexes.clear();
        this.duplicateTupleKeys = null;
        // 解析子节点
        int tupleIndex = 0;
        for (NodeTuple tuple : valueNode.getValue()) {
            Object key = this.adaptKey(constructor.getConstructed(tuple.getKeyNode()));
            Object value = constructor.getConstructed(tuple.getValueNode());

            YamlNode<?> node;
            if (value instanceof Map) {
                node = new SectionNode(this, tuple.getKeyNode(), (MappingNode) tuple.getValueNode(), key, constructor);
            }
            else if (value instanceof List) {
                node = new SequenceNode(this, tuple.getKeyNode(), (org.snakeyaml.engine.v2.nodes.SequenceNode) tuple.getValueNode(), key, constructor);
            }
            else {
                node = new net.momirealms.sparrow.yaml.node.ScalarNode(this, tuple.getKeyNode(), tuple.getValueNode(), key, value);
            }

            value().put(key, Objects.requireNonNull(node));
            this.registerTupleIndex(key, tupleIndex++);
        }
    }

}
