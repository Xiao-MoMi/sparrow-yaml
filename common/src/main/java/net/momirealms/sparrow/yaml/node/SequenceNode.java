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
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SequenceNode extends AbstractYamlNode<List<YamlNode<?>>> implements ParentNode<SequenceNode> {
    private final List<YamlNode<?>> readOnlyValue;

    public SequenceNode(
            @NotNull ParentNode<?> parent,
            @Nullable Node keyNode,
            @NotNull org.snakeyaml.engine.v2.nodes.SequenceNode valueNode,
            @Nullable Object key,
            @NotNull ExtendedConstructor constructor
    ) {
        super(parent, keyNode, valueNode, key, new ArrayList<>());
        this.readOnlyValue = Collections.unmodifiableList(this.value);
        this.init(keyNode, valueNode, constructor);
        if (parent != null) {
            this.root().attachNodeShallow(this);
        }
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
        this.readOnlyValue = Collections.unmodifiableList(this.value);
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

    /**
     * 返回按 index 排列的只读子节点视图.
     *
     * <p><strong>调用方不得修改返回的 List</strong>; 结构写入使用 {@link #setSubNode(int, Object)}
     * 或 {@link #removeSubNode(int)}.
     *
     * @return 当前 Sequence 的只读子节点视图
     */
    @Override
    @NotNull
    public List<YamlNode<?>> value() {
        return this.readOnlyValue;
    }

    List<YamlNode<?>> mutableValue() {
        return this.value;
    }

    org.snakeyaml.engine.v2.nodes.SequenceNode internalSequenceNode() {
        return (org.snakeyaml.engine.v2.nodes.SequenceNode) this.valueNode;
    }

    /**
     * 使用传入节点的深复制结果替换全部元素, 当前 Sequence 的注释、样式与 alias identity 保持不变.
     *
     * @param value 按目标 index 排列的元素
     */
    @Override
    public void setValue(@NotNull List<YamlNode<?>> value) {
        NodeMutation.replaceSequenceContents(this, value);
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
        return NodeMutation.remove(this, index);
    }
    /**
     * 在目标 index 写入子节点; 越界位置使用可持久化的 YAML null 补齐.
     * 普通值替换保留当前位置的表层注释与集合样式, {@link YamlNode} 输入使用深复制.
     *
     * @param index 非负目标 index
     * @param value 子节点值
     * @throws IndexOutOfBoundsException 当 index 为负数时
     */
    public void setSubNode(int index, @Nullable Object value) {
        NodeMutation.replace(this, index, value, null);
    }

    void replaceSubNode(int index, @Nullable Object value, @Nullable YamlNode<?> metadataSource) {
        NodeMutation.replace(this, index, value, metadataSource);
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

    void appendPreparedChild(YamlNode<?> child) {
        this.mutableValue().add(child);
        this.internalSequenceNode().getValue().add(child.internalValueNode());
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

            this.mutableValue().add(node);
        }
    }

}
