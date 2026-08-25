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
    private final Map<Object, YamlNode<?>> readOnlyValue;
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
        this.readOnlyValue = Collections.unmodifiableMap(this.value);
        this.init(keyNode, valueNode, constructor);
        if (parent != null) {
            this.root().attachNodeShallow(this);
        }
    }

    public SectionNode(
            @Nullable Node keyNode,
            @NotNull MappingNode valueNode
    ) {
        super();
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.value = new LinkedHashMap<>();
        this.readOnlyValue = Collections.unmodifiableMap(this.value);
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
        this.readOnlyValue = Collections.unmodifiableMap(this.value);
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

    /**
     * 返回按 YAML 顺序排列的只读子节点视图.
     *
     * <p><strong>调用方不得修改返回的 Map</strong>; 结构写入使用 {@link #setSubNode(Object, Object)}、
     * {@link #removeSubNode(Object)} 或 {@link #reorderKeys(List)}.
     *
     * @return 当前 Section 的只读子节点视图
     */
    @Override
    @NotNull
    public Map<Object, YamlNode<?>> value() {
        return this.readOnlyValue;
    }

    Map<Object, YamlNode<?>> mutableValue() {
        return this.value;
    }

    /**
     * 使用传入节点的深复制结果替换全部子节点, 当前 Section 的注释、样式与 alias identity 保持不变.
     *
     * @param value 按目标顺序排列的子节点
     * @throws NullPointerException 当 value 包含 null key 时
     */
    @Override
    public void setValue(@NotNull Map<Object, YamlNode<?>> value) {
        NodeMutation.replaceSectionContents(this, value);
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
        return NodeMutation.remove(this, key);
    }

    /**
     * 批量删除当前节点下的指定子节点，只执行一次底层 Tuple 与别名同步。
     *
     * @param keys 子节点的 Key
     */
    @ApiStatus.Internal
    public void removeSubNodes(@NotNull Collection<?> keys) {
        NodeMutation.remove(this, keys);
    }

    /**
     * 在当前 key 写入子节点; 普通值替换保留当前位置的表层注释与集合样式,
     * {@link YamlNode} 输入使用深复制并保留源节点的值元数据.
     *
     * @param key 子节点 key
     * @param value 子节点值
     */
    public void setSubNode(@NotNull Object key, @Nullable Object value) {
        NodeMutation.replace(this, key, value, null);
    }

    void replaceSubNode(Object key, @Nullable Object value, @Nullable YamlNode<?> metadataSource) {
        NodeMutation.replace(this, key, value, metadataSource);
    }
    
    public void syncValueNode(YamlNode<?> child) {
        this.syncNodeTuple(child.key(), child, false);
    }

    void appendPreparedChild(Object key, YamlNode<?> child) {
        this.mutableValue().put(key, child);
        MappingNode mappingNode = (MappingNode) this.valueNode;
        mappingNode.getValue().add(new NodeTuple(child.internalKeyNode(), child.internalValueNode()));
        this.registerTupleIndex(key, mappingNode.getValue().size() - 1);
    }

    void syncNodeTuple(Object key, YamlNode<?> targetNode, boolean collapseDuplicates) {
        Object adaptedKey = this.adaptKey(key);
        MappingNode mappingNode = (MappingNode) this.valueNode;
        List<NodeTuple> tuples = mappingNode.getValue();
        Node newValueNode = targetNode.internalValueNode();
        Integer tupleIndex = this.resolveTupleIndex(adaptedKey, tuples);
        if (tupleIndex == null) {
            Node newKeyNode = targetNode.internalKeyNode();
            if (newKeyNode == null) {
                newKeyNode = this.root().sparrowYaml().standardRepresenter().represent(adaptedKey);
            }
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
    private Integer resolveTupleIndex(Object key, List<NodeTuple> tuples) {
        Integer tupleIndex = this.tupleIndexes.get(key);
        return tupleIndex != null && tupleIndex < tuples.size() ? tupleIndex : null;
    }

    /**
     * 按指定 key 顺序重排子节点, 未列出的 key 保持相对顺序并追加在末尾.
     *
     * @param desiredOrder 优先排列的 key
     */
    public void reorderKeys(@NotNull List<Object> desiredOrder) {
        NodeMutation.reorder(this, desiredOrder);
    }

    void rebuildTupleState() {
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

            this.mutableValue().put(key, Objects.requireNonNull(node));
            this.registerTupleIndex(key, tupleIndex++);
        }
    }

}
