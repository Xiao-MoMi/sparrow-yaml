package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.SparrowYaml;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.List;
import java.util.Map;

public class ScalarNode extends AbstractYamlNode<Object> {

    public ScalarNode(
            @NotNull ParentNode<?> parent,
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable Object key,
            @Nullable Object value
    ) {
        super(parent, keyNode, valueNode, key, value);
    }

    private ScalarNode(
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable Object value
    ) {
        super();
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.value = value;
    }

    public static ScalarNode create(@NotNull YamlDocument root, @Nullable Object value) {
        return create(root.sparrowYaml(), value);
    }
    
    public static ScalarNode create(@NotNull SparrowYaml sparrowYaml, @Nullable Object value) {
        Node valueNode = sparrowYaml.standardRepresenter().represent(value);
        return new ScalarNode(null, valueNode, value);
    }

    /**
     * 用于新建节点时创建一个值为 null 的节点;
     */
    @ApiStatus.Internal
    public static ScalarNode emptyNode(@NotNull SparrowYaml sparrowYaml) {
        Node valueNode = sparrowYaml.standardRepresenter().represent(null);
        return new ScalarNode(null, valueNode, null);
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            throw new IllegalArgumentException("不能给 ScalarNode 设置 Map 或 List 的值!");
        }
        this.value = value;
        // 双向同步到底层的 SnakeYAML Node 中
        if (this.valueNode instanceof org.snakeyaml.engine.v2.nodes.ScalarNode sn) {
            SparrowYaml sparrowYaml = root().sparrowYaml();
            org.snakeyaml.engine.v2.nodes.ScalarNode represented = 
                (org.snakeyaml.engine.v2.nodes.ScalarNode) sparrowYaml.standardRepresenter().represent(value);
            // 通过反射或直接覆盖, 但因为 Node 没有公开的 setValue, 我们需要更新父节点的 Tuple/List
            this.valueNode = represented;
            if (this.parent instanceof SectionNode section) {
                section.syncValueNode(this);
            } else if (this.parent instanceof SequenceNode seq) {
                seq.syncValueNode(this);
            }
        }
    }

    @Override
    public YamlNodeType<?> yamlNodeType() {
        return YamlNodeType.SCALAR;
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
        return false;
    }

    @Override
    public boolean isScalar() {
        return true;
    }

}