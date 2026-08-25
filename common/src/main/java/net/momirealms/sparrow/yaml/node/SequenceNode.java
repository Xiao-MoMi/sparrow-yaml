package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.route.Route;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SequenceNode extends AbstractYamlNode<List<YamlNode<?>>> implements ParentNode<SequenceNode> {

    public SequenceNode(
            @NotNull ParentNode<?> parent,
            @Nullable Node keyNode,
            @NotNull org.snakeyaml.engine.v2.nodes.SequenceNode valueNode,
            @Nullable Object key,
            @NotNull ExtendedConstructor constructor
    ) {
        super(parent, keyNode, valueNode, key, new ArrayList<>());
        init(keyNode, valueNode, constructor);
    }

    // 创建一个手动初始化的 SequenceNode;
    @ApiStatus.Internal
    public SequenceNode(
            @Nullable Node keyNode,
            @NotNull org.snakeyaml.engine.v2.nodes.SequenceNode valueNode
    ) {
        super();
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.value = new ArrayList<>();
    }

    public static SequenceNode createEmpty(@NotNull YamlDocument root) {
        return createEmpty(root.sparrowYaml());
    }

    public static SequenceNode createEmpty(@NotNull SparrowYaml sparrowYaml) {
        org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode = (org.snakeyaml.engine.v2.nodes.SequenceNode) sparrowYaml.standardRepresenter().represent(new ArrayList<>());
        return new SequenceNode(null, sequenceNode);
    }

    @Override
    public SequenceNode yamlNode() {
        return this;
    }

    @Override
    public void setValue(List<YamlNode<?>> value) {
        this.value.forEach(it -> it.parentNode(null));
        value.forEach(it -> it.parentNode(this));
        this.value = value;
    }

    @Override
    public YamlNodeType<?> yamlNodeType() {
        return YamlNodeType.SEQUENCE;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public boolean isSection() {
        return false;
    }

    @Override
    public boolean isSequence() {
        return true;
    }

    @Override
    public boolean isScalar() {
        return false;
    }

    /**
     * 删除当前节点下的指定索引节点。
     * @param key 索引
     * @return 删掉的节点，如果不存在则返回 null
     */
    @Nullable
    @Override
    public YamlNode<?> removeSubNode(Object key) {
        if (key instanceof Integer index) {
            return removeSubNode((int) index);
        }
        return null;
    }
    
    @Nullable
    public YamlNode<?> removeSubNode(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        List<AbstractYamlNode<?>> references = this.root().nodeReferences(this);
        YamlNode<?> removed = this.value().remove(index);
        if (removed != null) {
            removed.parentNode(null);
            
            // 从底层的 Node 列表中移除
            org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode = (org.snakeyaml.engine.v2.nodes.SequenceNode) this.valueNode;
            sequenceNode.getValue().remove(index);
            
            // 更新后续元素的 key (index)
            for (int i = index; i < this.size(); i++) {
                YamlNode<?> node = this.value().get(i);
                if (node != null) {
                    node.key(i);
                }
            }
            this.syncAliasValues(references);
        }
        return removed;
    }
    /**
     * 在当前节点下创建一个新的子节点, 子节点的值默认为 null ;
     * 如果当前存在这样的一个子节点, 那么会覆盖这个子节点;
     * 如果目标索引越界, 那么会补全节点至目标节点, 补全的节点均为 ScalarNode, 值为 null ;
     * 传入 {@link YamlNode} 时会复制节点值、注释与集合样式, 不改变源节点;
     * @param index 目标索引;
     * @param value 子节点的 Value;
     */
    public void setSubNode(int index, Object value) {
        this.replaceSubNode(index, value, null);
    }

    void replaceSubNode(int index, @Nullable Object value, @Nullable YamlNode<?> metadataSource) {
        List<AbstractYamlNode<?>> references = this.root().nodeReferences(this);
        YamlNode<?> targetNode = this.createSubNode(index, value);
        if (metadataSource != null) {
            metadataSource.preserveFlowStyleTo(targetNode);
            metadataSource.copyCommentsTo(targetNode);
        }
        YamlNode<?> yamlNode = index <= size() - 1 ? this.value().get(index) : null;
        if (yamlNode != null) {
            yamlNode.parentNode(null);
        }
        
        org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode = (org.snakeyaml.engine.v2.nodes.SequenceNode) this.valueNode;
        List<Node> nodes = sequenceNode.getValue();
        
        // 补全越界的节点为空 ScalarNode
        if (index >= size()) {
            for (int i = this.size(); i <= index; i++) {
                if (i == index) {
                    this.value().add(null);
                    nodes.add(null); // placeholder
                } else {
                    ScalarNode empty = ScalarNode.emptyNode(root().sparrowYaml());
                    empty.parentNode(this);
                    empty.key(i);
                    this.value().add(empty);
                    nodes.add(empty.internalValueNode());
                }
            }
        }
        this.value().set(index, targetNode);
        nodes.set(index, targetNode.internalValueNode());
        this.syncAliasValues(references);
    }

    private YamlNode<?> createSubNode(int index, Object value) {
        if (value instanceof YamlNode<?> existingNode) {
            return AbstractYamlNode.copyOf(existingNode, this, index, null);
        }
        if (value instanceof Map map) {
            SectionNode sectionNode = SectionNode.createEmpty(this.root());
            sectionNode.parentNode(this);
            sectionNode.key(index);
            map.forEach(sectionNode::setSubNode);
            return sectionNode;
        }
        if (value instanceof List list) {
            SequenceNode sequenceNode = SequenceNode.createEmpty(this.root());
            sequenceNode.parentNode(this);
            sequenceNode.key(index);
            for (int i = 0; i < list.size(); i++) {
                sequenceNode.setSubNode(i, list.get(i));
            }
            return sequenceNode;
        }
        Node valueNode = this.root().sparrowYaml().standardRepresenter().represent(value);
        if (valueNode == null || valueNode instanceof org.snakeyaml.engine.v2.nodes.ScalarNode scalarNode && scalarNode.getValue() == null) {
            valueNode = this.root().sparrowYaml().standardRepresenter().represent("null");
        }
        return new net.momirealms.sparrow.yaml.node.ScalarNode(this, null, valueNode, index, value);
    }

    private void syncAliasValues(List<AbstractYamlNode<?>> references) {
        for (int i = 0; i < references.size(); i++) {
            AbstractYamlNode<?> reference = references.get(i);
            if (reference == this || !(reference instanceof SequenceNode alias)) {
                continue;
            }
            for (YamlNode<?> child : alias.value()) {
                child.parentNode(null);
            }
            List<YamlNode<?>> values = new ArrayList<>(this.size());
            for (int index = 0; index < this.size(); index++) {
                YamlNode<?> child = this.value().get(index);
                values.add(AbstractYamlNode.aliasOf(child, alias, index, child.internalKeyNode()));
            }
            alias.value = values;
        }
    }
    
    public void syncValueNode(YamlNode<?> child) {
        if (child.key() instanceof Integer index) {
            org.snakeyaml.engine.v2.nodes.SequenceNode sequenceNode = (org.snakeyaml.engine.v2.nodes.SequenceNode) this.valueNode;
            List<Node> nodes = sequenceNode.getValue();
            if (index >= 0 && index < nodes.size()) {
                nodes.set(index, child.internalValueNode());
            }
        }
    }

    /**
     * 返回当前节点的数组长度;
     * @return 长度;
     */
    public int size() {
        return this.value().size();
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
     * 递归获取当前 Sequence 的普通 Java 值, YAML null 保留为 Java null.
     *
     * @return 按 YAML 顺序排列的 List
     */
    @NotNull
    public List<Object> getValues() {
        return NodeValueProjection.sequenceValues(this);
    }

    // 初始化节点
    protected void init(@Nullable Node keyNode, @NotNull org.snakeyaml.engine.v2.nodes.SequenceNode valueNode, ExtendedConstructor constructor) {
        // 初始化注释
        super.initComments(keyNode, valueNode);
        // 解析子节点
        for (int i = 0; i < valueNode.getValue().size(); i++) {
            Node singleNode = valueNode.getValue().get(i);
            Object value = constructor.getConstructed(singleNode);

            YamlNode<?> node;
            if (value instanceof Map) {
                node = new SectionNode(this, null, (MappingNode) singleNode, i, constructor);
            }
            else if (value instanceof List) {
                node = new SequenceNode(this, null, (org.snakeyaml.engine.v2.nodes.SequenceNode) singleNode, i, constructor);
            }
            else {
                node = new ScalarNode(this, null, singleNode, i, value);
            }

            value().add(node);
        }
    }

}
