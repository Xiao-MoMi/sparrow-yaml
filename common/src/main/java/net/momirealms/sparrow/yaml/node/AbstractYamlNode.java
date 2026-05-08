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

public abstract class AbstractYamlNode<T> implements YamlNode<T> {
    protected ParentNode<?> parent;
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
            @NotNull ParentNode<?> parent,
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable Object key,
            @Nullable T value
    ) {
        this.parent = parent;
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.key = key;
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
        Route route = route();
        if (route == null) return null;
        return route.getRouteElement(route.length() - 1);
    }

    @Override
    public @NotNull YamlDocument root() {
        if (this instanceof YamlDocument doc) {
            return doc;
        }
        if (parent != null) {
            return parent.yamlNode().root();
        }
        throw new IllegalStateException("当前 Node 尚未挂载到任何 YamlDocument 上!");
    }

    @Override
    public @Nullable Node internalKeyNode() {
        return keyNode;
    }

    @Override
    public @Nullable Node internalValueNode() {
        return valueNode;
    }

    @ApiStatus.Internal
    public void internalKeyNode(Node keyNode) {
        this.keyNode = keyNode;
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
    public void key(Object key) {
        this.key = key;
    }

    @Override
    public Route route() {
        if (parent == null) {
            return this instanceof YamlDocument ? Route.empty() : null;
        }
        Route parentRoute = parent.yamlNode().route();
        if (parentRoute == null) {
            return Route.from(key);
        }
        return Route.addTo(parentRoute, key);
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

    @Override
    public void setBeforeKeyComments(List<CommentLine> comments) {
        this.beforeKeyComments = comments;
        if (keyNode != null) {
            keyNode.setBlockComments(comments);
        }
    }

    @Override
    public void setInlineKeyComments(List<CommentLine> comments) {
        this.inlineKeyComments = comments;
        if (keyNode != null) {
            keyNode.setInLineComments(comments);
        }
    }

    @Override
    public void setAfterKeyComments(List<CommentLine> comments) {
        this.afterKeyComments = comments;
        if (keyNode != null) {
            keyNode.setEndComments(comments);
        }
    }

    @Override
    public void setBeforeValueComments(List<CommentLine> comments) {
        this.beforeValueComments = comments;
        if (valueNode != null) {
            valueNode.setBlockComments(comments);
        }
    }

    @Override
    public void setInlineValueComments(List<CommentLine> comments) {
        this.inlineValueComments = comments;
        if (valueNode != null) {
            valueNode.setInLineComments(comments);
        }
    }

    @Override
    public void setAfterValueComments(List<CommentLine> comments) {
        this.afterValueComments = comments;
        if (valueNode != null) {
            valueNode.setEndComments(comments);
        }
    }

}
