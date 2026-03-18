package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.IndexElement;
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
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @NotNull org.snakeyaml.engine.v2.nodes.SequenceNode valueNode,
            @NotNull ExtendedConstructor constructor
    ) {
        super(root, parent, route, keyNode, valueNode, new ArrayList<>());
        init(root, keyNode, valueNode, constructor);
    }

    // 创建一个手动初始化的 SequenceNode;
    @ApiStatus.Internal
    public SequenceNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @NotNull List<?> list
    ) {
        super();
        this.root = root;
        this.parent = parent;
        this.route = route;
        this.keyNode = keyNode;
        this.valueNode = root.sparrowYaml().standardRepresenter().represent(list);
        this.key = route.getLastRouteElement().key();
        this.value = new ArrayList<>();

        // 初始化节点
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            YamlNode<?> putNode;
            if (value instanceof Map mapValue) {
                putNode = new SectionNode(root, this, route.add(new IndexElement(i)), null, mapValue);
            }
            else if (value instanceof List listValue) {
                putNode = new SequenceNode(root, this, route.add(new IndexElement(i)), null, listValue);
            }
            else {
                putNode = new ScalarNode(root, this, route.add(new IndexElement(i)), null, value);
            }
            this.value.add(putNode);
        }
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
     * 在当前节点下创建一个新的子节点, 子节点的值默认为 null ;
     * 如果当前存在这样的一个子节点, 那么会覆盖这个子节点;
     * 如果目标索引越界, 那么会补全节点至目标节点, 补全的节点均为 ScalarNode, 值为 null ;
     * @param index 目标索引;
     * @param value 子节点的 Value;
     */
    public void setSubNode(int index, Object value) {
        // 解除当前存在的节点的关联引用;
        YamlNode<?> yamlNode = index <= size() - 1 ? this.value().get(index) : null;
        if (yamlNode != null) {
            yamlNode.parentNode(null);
        }
        // 补全越界的节点为空 ScalarNode
        if (index > size()) {
            for (int i = this.size(); i < index; i++) {
                this.value().add(ScalarNode.emptyNode(root, this, route.add(new IndexElement(i)), null));
            }
        }
        // 给目标节点预分配空间, 然后设置新的节点.
        this.value().add(null);
        YamlNode<?> targetNode;
        if (value instanceof YamlNode valueOfNode) {
            targetNode = valueOfNode;
        }
        else if (value instanceof Map map) {
            targetNode = new SectionNode(root, this, this.route.add(new IndexElement(index)), null, map);
        }
        else if (value instanceof List list) {
            targetNode = new SequenceNode(root, this, this.route.add(new IndexElement(index)), null, list);
        }
        else {
            targetNode = new ScalarNode(root, this, this.route.add(new IndexElement(index)), null, value);
        }
        this.value().set(index, targetNode);
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

    // 初始化节点
    protected void init(@NotNull YamlDocument root, @Nullable Node keyNode, @NotNull org.snakeyaml.engine.v2.nodes.SequenceNode valueNode, ExtendedConstructor constructor) {
        // 初始化注释
        super.initComments(keyNode, valueNode);
        // 解析子节点
        this.root = root;
        for (int i = 0; i < valueNode.getValue().size(); i++) {
            Node singleNode = valueNode.getValue().get(i);
            Object value = constructor.getConstructed(singleNode);

            YamlNode<?> node;
            if (value instanceof Map) {
                node = new SectionNode(root, this, this.getSubRoute(new IndexElement(i)), null, (MappingNode) singleNode, constructor);
            }
            else if (value instanceof List) {
                node = new SequenceNode(root, this, this.getSubRoute(new IndexElement(i)), null, (org.snakeyaml.engine.v2.nodes.SequenceNode) singleNode, constructor);
            }
            else {
                node = new ScalarNode(root, this, this.getSubRoute(new IndexElement(i)), null, singleNode, value);
            }

            value().add(node);
        }
    }

}
