package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SectionNode extends AbstractYamlNode<Map<Object, YamlNode<?>>> implements ParentNode<SectionNode> {

    public SectionNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @NotNull MappingNode valueNode,
            @NotNull ExtendedConstructor constructor
    ) {
        super(root, parent, route, keyNode, valueNode, new LinkedHashMap<>());
        init(root, keyNode, valueNode, constructor);
    }

    public SectionNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @NotNull Map<?, ?> mappings
    ) {
        super();
        this.root = root;
        this.parent = parent;
        this.route = route;
        this.keyNode = keyNode;
        this.valueNode = root.sparrowYaml().standardRepresenter().represent(mappings);
        this.key = route.getLastRouteElement().key();
        this.value = new LinkedHashMap<>();

        // 初始化节点
        for (Map.Entry<?, ?> entry : mappings.entrySet()) {
            Object key = this.adaptKey(entry.getKey());
            Object value = entry.getValue();

            YamlNode<?> putNode = null;
            if (value instanceof Map mapValue) {
                putNode = new SectionNode(root, this, route.add(key), parent.yamlNode().internalKeyNode(), mapValue);
            }
            else if (value instanceof List listValue) {
                putNode = new SequenceNode(root, this, route.add(key), parent.yamlNode().internalKeyNode(), listValue);
            }
            else {
                putNode = new ScalarNode(root, this, route.add(key), parent.yamlNode().internalKeyNode(), value);
            }
            this.value.put(key, putNode);
        }
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
        return root.sparrowYaml().allowObjectKey ? key : key.toString();
    }

    /**
     * 在当前节点下创建一个新的子节点, 子节点的值默认为 null ;
     * 如果当前存在这样的一个子节点, 那么就会覆盖这个子节点;
     * @param key 子节点的 Key;
     * @param value 子节点的 Value;
     */
    public <T> void setSubNode(Object key, Object value) {
        // 解除当前存在的节点的关联引用;
        YamlNode<?> yamlNode = this.getNodeOrNull(Route.from(key));
        if (yamlNode != null) {
            yamlNode.parentNode(null);
        }
        // 设置新的节点
        YamlNode<?> targetNode;
        Node keyNode = root.sparrowYaml().standardRepresenter().represent(key);
        if (value instanceof Map map) {
            targetNode = new SectionNode(root, this, Route.addTo(route, new KeyElement(key)), keyNode, map);
        }
        else if (value instanceof List list) {
            targetNode = new SequenceNode(root, this, Route.addTo(route, new KeyElement(key)), keyNode, list);
        }
        else {
            targetNode = new ScalarNode(root, this, Route.addTo(route, new KeyElement(key)), keyNode, value);
        }
        this.value().put(key, targetNode);
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
     * @param root 根节点
     * @param keyNode 当前 Section 的 KeyNode
     * @param valueNode 当前 Section 的 ValueNode
     * @param constructor 构造器用于解析根节点下的所有节点, 将节点解析成 JavaBean
     */
    protected void init(@NotNull YamlDocument root, @Nullable Node keyNode, @NotNull MappingNode valueNode, ExtendedConstructor constructor) {
        // 赋值根节点, 合法性检查;
        if (root == this) {
            this.valueNode = valueNode;
            if (keyNode != null) {
                throw new IllegalArgumentException("根节点不能拥有 Key 值!");
            }
        }
        // 初始化注释
        super.initComments(keyNode, valueNode);
        // 解析子节点
        this.root = root;
        for (NodeTuple tuple : valueNode.getValue()) {
            Object key = this.adaptKey(constructor.getConstructed(tuple.getKeyNode()));
            Object value = constructor.getConstructed(tuple.getValueNode());

            YamlNode<?> node;
            if (value instanceof Map) {
                node = new SectionNode(root, this, this.getSubRoute(key), tuple.getKeyNode(), (MappingNode) tuple.getValueNode(), constructor);
            }
            else if (value instanceof List) {
                node = new SequenceNode(root, this, this.getSubRoute(key), tuple.getKeyNode(), (org.snakeyaml.engine.v2.nodes.SequenceNode) tuple.getValueNode(), constructor);
            }
            else {
                node = new ScalarNode(root, this, this.getSubRoute(key), tuple.getKeyNode(), tuple.getValueNode(), value);
            }

            value().put(key, Objects.requireNonNull(node));
        }
    }

}
