package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.route.Route;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;

import java.lang.reflect.Field;
import java.util.*;

public class SectionNode extends AbstractYamlNode<Map<Object, YamlNode<?>>> implements ParentNode<SectionNode> {

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
     * 如果此 Section 是根节点（检查isRoot() ）, 则返回null.
     * @return 仅包含该 Section 的 Key 的路由
     */
    @NotNull
    public Route getKeyAsRoute() {
        return Route.from(super.key);
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
        Object adaptedKey = adaptKey(key);
        YamlNode<?> removed = this.value().remove(adaptedKey);
        if (removed != null) {
            removed.parentNode(null);
            
            // 从底层的 NodeTuple 列表中移除对应的 Tuple
            MappingNode mappingNode = (MappingNode) this.valueNode;
            java.util.List<NodeTuple> tuples = mappingNode.getValue();
            java.util.Iterator<NodeTuple> iterator = tuples.iterator();
            while (iterator.hasNext()) {
                NodeTuple tuple = iterator.next();
                Object tupleKey = tuple.getKeyNode() instanceof ScalarNode sn 
                                ? sn.getValue() 
                                : null;
                if (tupleKey != null && Objects.equals(adaptKey(tupleKey), adaptedKey)) {
                    iterator.remove();
                    // 这里不能直接清空内部，可能抛出 NPE 或破坏深拷贝引用
                }
            }
        }
        return removed;
    }

    /**
     * 在当前节点下创建一个新的子节点, 子节点的值默认为 null ;
     * 如果当前存在这样的一个子节点, 那么就会覆盖这个子节点;
     * @param key 子节点的 Key;
     * @param value 子节点的 Value;
     */
    public <T> void setSubNode(Object key, Object value) {
        Object adaptedKey = adaptKey(key);
        // 解除当前存在的节点的关联引用;
        YamlNode<?> yamlNode = this.value().get(adaptedKey);
        if (yamlNode != null) {
            // 顺便从底层 tuple 中先移除，后续重建。这是为了避免直接覆盖导致的问题
            MappingNode mappingNode = (MappingNode) this.valueNode;
            List<NodeTuple> tuples = mappingNode.getValue();
            Iterator<NodeTuple> iterator = tuples.iterator();
            while (iterator.hasNext()) {
                NodeTuple tuple = iterator.next();
                Object tupleKey = tuple.getKeyNode() instanceof ScalarNode sn 
                                ? sn.getValue() 
                                : null;
                if (tupleKey != null && Objects.equals(adaptKey(tupleKey), adaptedKey)) {
                    iterator.remove();
                }
            }
            
            // 解决NPE的关键：一旦移除，我们也要切断引用
            yamlNode.parentNode(null);
            if (yamlNode instanceof AbstractYamlNode<?> abs) {
                try {
                    Field valueNodeField = AbstractYamlNode.class.getDeclaredField("valueNode");
                    valueNodeField.setAccessible(true);
                    valueNodeField.set(abs, null);
                } catch (Exception ignored) {}
            }
        }
        // 设置新的节点
        YamlNode<?> targetNode;
        if (value instanceof YamlNode<?> existingNode) {
            targetNode = existingNode;
            targetNode.parentNode(this);
            targetNode.key(adaptedKey);
            // 这里会导致将另一个 document 中的内部引用强行挂载到当前 document
            // 这也是触发 NullPointerException 的关键原因！如果强行挂载，底层 SnakeYAML 的 IdentityHashMap 将出错。
            // 因此我们需要确保所有的值都被转化为普通的 Java 对象！我们在 YamlUpdater 中已经做到了，
            // 但如果这里被传入的是原有节点引用，我们应当拒绝或使用拷贝。
        } else {
            if (value instanceof Map map) {
                targetNode = SectionNode.createEmpty(root());
                targetNode.parentNode(this);
                targetNode.key(adaptedKey);
                SectionNode sectionNode = (SectionNode) targetNode;
                map.forEach((k, v) -> sectionNode.setSubNode(k, v));
            } else if (value instanceof List list) {
                targetNode = SequenceNode.createEmpty(root());
                targetNode.parentNode(this);
                targetNode.key(adaptedKey);
                SequenceNode sequenceNode = (SequenceNode) targetNode;
                for (int i = 0; i < list.size(); i++) {
                    sequenceNode.setSubNode(i, list.get(i));
                }
            } else {
                Node valueNode = root().sparrowYaml().standardRepresenter().represent(value);
                if (valueNode == null || (valueNode instanceof org.snakeyaml.engine.v2.nodes.ScalarNode sn && sn.getValue() == null)) {
                    valueNode = root().sparrowYaml().standardRepresenter().represent("null");
                }
                targetNode = new net.momirealms.sparrow.yaml.node.ScalarNode(this, null, valueNode, adaptedKey, value);
            }
        }
        this.value().put(adaptedKey, targetNode);
        
        // 双向同步到底层 NodeTuple 列表
        syncNodeTuple(adaptedKey, targetNode);
    }
    
    public void syncValueNode(YamlNode<?> child) {
        syncNodeTuple(child.key(), child);
    }
    
    private void syncNodeTuple(Object key, YamlNode<?> targetNode) {
        MappingNode mappingNode = (MappingNode) this.valueNode;
        List<NodeTuple> tuples = mappingNode.getValue();
        Node newKeyNode = root().sparrowYaml().standardRepresenter().represent(key);
        Node newValueNode = targetNode.internalValueNode();
        
        for (int i = 0; i < tuples.size(); i++) {
            NodeTuple tuple = tuples.get(i);
            // In SnakeYAML, the key node is usually a ScalarNode, its value is the string key.
            if (tuple.getKeyNode() instanceof org.snakeyaml.engine.v2.nodes.ScalarNode sn) {
                if (adaptKey(sn.getValue()).equals(key)) {
                    tuples.set(i, new NodeTuple(tuple.getKeyNode(), newValueNode));
                    if (targetNode instanceof AbstractYamlNode<?> abs) {
                        abs.internalKeyNode(tuple.getKeyNode());
                    }
                    return;
                }
            }
        }
        // Not found, add new tuple
        tuples.add(new NodeTuple(newKeyNode, newValueNode));
        if (targetNode instanceof AbstractYamlNode<?> abs) {
            abs.internalKeyNode(newKeyNode);
        }
    }

    /**
     * Reorders the child nodes and their corresponding internal NodeTuples based on the provided list of keys.
     * Keys not present in the desiredOrder list will be appended at the end in their original relative order.
     */
    public void reorderKeys(List<Object> desiredOrder) {
        Map<Object, YamlNode<?>> newMap = new java.util.LinkedHashMap<>();
        MappingNode mappingNode = (MappingNode) this.valueNode;
        List<NodeTuple> oldTuples = new java.util.ArrayList<>(mappingNode.getValue());
        List<NodeTuple> newTuples = new java.util.ArrayList<>();

        // Add keys in the desired order
        for (Object key : desiredOrder) {
            Object adaptedKey = adaptKey(key);
            YamlNode<?> node = this.value.get(adaptedKey);
            if (node != null) {
                newMap.put(adaptedKey, node);
                // Find and add the corresponding tuple
                for (NodeTuple tuple : oldTuples) {
                    if (tuple.getKeyNode() instanceof org.snakeyaml.engine.v2.nodes.ScalarNode sn) {
                        if (adaptKey(sn.getValue()).equals(adaptedKey)) {
                            newTuples.add(tuple);
                            break;
                        }
                    }
                }
            }
        }

        // Add remaining keys
        for (Map.Entry<Object, YamlNode<?>> entry : this.value.entrySet()) {
            if (!newMap.containsKey(entry.getKey())) {
                newMap.put(entry.getKey(), entry.getValue());
                for (NodeTuple tuple : oldTuples) {
                    if (tuple.getKeyNode() instanceof org.snakeyaml.engine.v2.nodes.ScalarNode sn) {
                        if (adaptKey(sn.getValue()).equals(entry.getKey())) {
                            newTuples.add(tuple);
                            break;
                        }
                    }
                }
            }
        }

        this.value.clear();
        this.value.putAll(newMap);
        mappingNode.setValue(newTuples);
    }

    /**
     * 获取当前节点的子路由
     * @param key 目标路由节点
     * @return 新的路由对象
     */
    public Route getSubRoute(@NotNull Object key) {
        return Route.addTo(super.route(), key);
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
        // 解析子节点
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
        }
    }

}
