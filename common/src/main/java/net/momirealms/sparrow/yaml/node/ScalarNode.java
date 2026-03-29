package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.List;
import java.util.Map;

public class ScalarNode extends AbstractYamlNode<Object> {

    public ScalarNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable Object value
    ) {
        super(root, parent, route, keyNode, valueNode, value);
    }

    public ScalarNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @Nullable Object value
    ) {
        super();
        this.root = root;
        this.parent = parent;
        this.route = route;
        this.keyNode = keyNode;
        this.valueNode = root.sparrowYaml().standardRepresenter().represent(value);
        this.key = route.getLastRouteElement().key();
        this.value = value;
    }

    /**
     * 用于新建节点时创建一个值为 null 的节点;
     */
    @ApiStatus.Internal
    public static ScalarNode emptyNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Object key
    ) {
        Node keyNode = key != null ? root.sparrowYaml().standardRepresenter().represent(key) : null;
        Node valueNode = root.sparrowYaml().standardRepresenter().represent(null);
        if (keyNode == null || keyNode instanceof org.snakeyaml.engine.v2.nodes.ScalarNode) {
            return new ScalarNode(root, parent, route, keyNode, valueNode, null);
        }
        throw new IllegalArgumentException("创建 Node 时, 其 Key 不能为 Map 或 List !");
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            throw new IllegalArgumentException("不能给 ScalarNode 设置 Map 或 List 的值!");
        }
        this.value = value;
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
