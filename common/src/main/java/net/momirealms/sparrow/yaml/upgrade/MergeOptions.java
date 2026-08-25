package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.route.Route;

import java.util.List;

/**
 * 控制升级后合并行为的选项集合.
 */
public record MergeOptions(
        boolean updateComments,
        boolean deleteRemovedNodes,
        List<Route> globallyIgnoredRoutes
) {

    /**
     * 返回与 {@link YamlUpgradePipeline#builder()} 一致的默认选项, 保留已有本地注释并清理已移除节点.
     *
     * @return 默认合并选项
     */
    public static MergeOptions defaultOptions() {
        return new MergeOptions(false, true, List.of());
    }

}
