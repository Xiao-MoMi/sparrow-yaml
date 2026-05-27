package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;

import java.util.function.Function;

/**
 * 为缺失节点补充默认值的补丁规则.
 */
public class DefaultValueRule implements Patch {

    /** 需要填充默认值的目标路径. */
    private final Route route;
    /** 默认值提供器, 可根据当前文档动态生成值. */
    private final Function<YamlDocument, Object> defaultValueProvider;

    /**
     * 创建一个动态默认值规则.
     *
     * @param route 目标路径
     * @param defaultValueProvider 默认值提供逻辑
     */
    public DefaultValueRule(Route route, Function<YamlDocument, Object> defaultValueProvider) {
        this.route = route;
        this.defaultValueProvider = defaultValueProvider;
    }

    /**
     * 创建一个固定默认值规则.
     */
    public DefaultValueRule(Route route, Object defaultValue) {
        this(route, doc -> defaultValue);
    }

    /**
     * 当目标节点缺失时写入默认值, 并尽量继承模板节点的注释与风格.
     */
    @Override
    public YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context) {
        YamlNode<?> node = localDoc.getNodeOrNull(route);
        if (node == null) {
            Object defaultValue = defaultValueProvider.apply(localDoc);
            YamlNode<?> newNode = localDoc.setAndGet(route, defaultValue);
            
            // 如果模板中存在同路径节点, 则同步其注释和风格.
            YamlNode<?> defNode = defDoc.getNodeOrNull(route);
            if (defNode != null && newNode != null) {
                defNode.preserveFlowStyleTo(newNode);
                defNode.deepCopyCommentsTo(newNode);
            }
        }
        return localDoc;
    }

    /**
     * 默认值规则在转换之后、校验之前执行.
     */
    @Override
    public int getOrder() {
        return 40;
    }
}
