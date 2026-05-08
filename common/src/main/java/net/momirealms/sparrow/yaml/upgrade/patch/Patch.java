package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;

/**
 * 表示升级流程中的单个补丁规则.
 * 补丁负责在合并前对本地文档执行定向改写.
 */
@FunctionalInterface
public interface Patch extends Comparable<Patch> {

    /**
     * 执行补丁逻辑.
     *
     * @param defDoc 模板文档
     * @param localDoc 当前正在升级的本地文档
     * @param context 当前升级流程中所有补丁共享的上下文
     * @return 应用补丁后的文档
     */
    YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context);

    /**
     * 使用新的空上下文执行补丁.
     */
    default YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc) {
        return apply(defDoc, localDoc, new PatchContext());
    }

    /**
     * 返回补丁执行顺序.
     * 数值越小越先执行.
     */
    default int getOrder() {
        return 0;
    }

    @Override
    default int compareTo(Patch o) {
        return Integer.compare(this.getOrder(), o.getOrder());
    }
}
