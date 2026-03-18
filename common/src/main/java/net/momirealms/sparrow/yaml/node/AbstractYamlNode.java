package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.RouteElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractYamlNode<T> implements YamlNode<T> {
    protected YamlDocument root;
    protected ParentNode<?> parent;
    protected Route route;
    protected Node keyNode;
    protected Node valueNode;
    protected Object key;
    protected T value;

    protected List<CommentLine> beforeKeyComments = null;
    protected List<CommentLine> inlineKeyComments = null;
    protected List<CommentLine> afterKeyComments = null;
    protected List<CommentLine> beforeValueComments = null;
    protected List<CommentLine> inlineValueComments = null;
    protected List<CommentLine> afterValueComments = null;

    // 创建虚拟节点/无法到达节点时, 使用的构造函数;
    @ApiStatus.Internal
    protected AbstractYamlNode() {}

    // 创建 YamlDocument 也就是根节点时, 使用的构造函数;
    @ApiStatus.Internal
    protected AbstractYamlNode(@NotNull T value) {
        this.value = value;
    }

    // 正常读取到的 Node 的构造函数;
    public AbstractYamlNode(
            @NotNull YamlDocument root,
            @NotNull ParentNode<?> parent,
            @NotNull Route route,
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable T value
    ) {
        this.root = root;
        this.parent = parent;
        this.route = route;
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.key = route.getRouteKey(route.length() - 1);
        this.value = value;
        initComments(keyNode, valueNode);
    }

    /**
     * 收集当前节点的注释信息
     * @param key 节点的 Key
     * @param value 节点的 Value
     */
    protected void initComments(Node key, Node value) {
        if (key != null) {
            beforeKeyComments = key.getBlockComments() == null ? new ArrayList<>(0) : key.getBlockComments();
            inlineKeyComments = key.getInLineComments();
            afterKeyComments = key.getEndComments();
        }
        if (value != null) {
            beforeValueComments = value.getBlockComments() == null ? new ArrayList<>(0) : value.getBlockComments();
            inlineValueComments = value.getInLineComments();
            afterValueComments = value.getEndComments();
        }
    }

    @Override
    public Object key() {
        return key;
    }


    @Override
    public @Nullable RouteElement<?> keyRouteElement() {
        return route.getRouteElement(route.length() - 1);
    }

    @Override
    public @NotNull YamlDocument root() {
        return root;
    }

    @Override
    public @Nullable Node internalKeyNode() {
        return keyNode;
    }

    @Override
    public @Nullable Node internalValueNode() {
        return valueNode;
    }

    @Override
    public @Nullable ParentNode<?> parentNode() {
        return parent;
    }

    @Override
    public void parentNode(ParentNode<?> parentNode) {
        this.parent = parentNode;
    }

    @Override
    public Route route() {
        return route;
    }

    @Override
    public T value() {
        return value;
    }

    @Override
    public List<CommentLine> beforeKeyComments() {
        return beforeKeyComments;
    }

    @Override
    public List<CommentLine> inlineKeyComments() {
        return inlineKeyComments;
    }

    @Override
    public List<CommentLine> afterKeyComments() {
        return afterKeyComments;
    }

    @Override
    public List<CommentLine> beforeValueComments() {
        return beforeValueComments;
    }

    @Override
    public List<CommentLine> inlineValueComments() {
        return inlineValueComments;
    }

    @Override
    public List<CommentLine> afterValueComments() {
        return afterValueComments;
    }


}
