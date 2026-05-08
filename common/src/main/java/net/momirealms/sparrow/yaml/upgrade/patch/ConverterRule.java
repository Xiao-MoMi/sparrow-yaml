package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;

import java.util.function.Function;

/**
 * 对指定路径的节点执行值转换的补丁规则.
 */
public class ConverterRule implements Patch {

    /** 需要执行转换的目标路径. */
    private final Route route;
    /** 节点转换逻辑, 输入原节点, 输出转换后的值. */
    private final Function<YamlNode<?>, Object> converter;

    /**
     * 创建一个节点转换规则.
     *
     * @param route 需要转换的节点路径
     * @param converter 节点值转换逻辑
     */
    public ConverterRule(Route route, Function<YamlNode<?>, Object> converter) {
        this.route = route;
        this.converter = converter;
    }

    /**
     * 对本地文档中的目标节点执行转换, 并尽量保留原节点注释与风格.
     */
    @Override
    public YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context) {
        YamlNode<?> node = localDoc.getNodeOrNull(route);
        if (node != null) {
            try {
                Object convertedValue = converter.apply(node);
                YamlNode<?> newNode = localDoc.setAndGet(route, convertedValue);
                if (newNode != null) {
                    node.preserveFlowStyleTo(newNode);
                    node.deepCopyCommentsTo(newNode);
                }
            } catch (Exception e) {
                // 保留原始异常上下文, 便于定位具体转换失败位置.
                throw new RuntimeException("Failed to apply ConverterRule at route " + route, e);
            }
        }
        return localDoc;
    }

    /**
     * 转换规则在迁移之后、默认值填充之前执行.
     */
    @Override
    public int getOrder() {
        return 30;
    }
}
