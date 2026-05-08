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
     * 返回一组默认合并选项.
     */
    public static MergeOptions defaultOptions() {
        return new MergeOptions(true, false, List.of());
    }

}
