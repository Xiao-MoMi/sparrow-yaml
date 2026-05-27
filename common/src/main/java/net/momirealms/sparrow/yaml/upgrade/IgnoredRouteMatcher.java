package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.route.Route;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于前缀树的忽略路由匹配器.
 * 用于快速判断某个路由是否命中忽略规则或其子路径.
 */
public final class IgnoredRouteMatcher {
    private static final IgnoredRouteMatcher EMPTY = new IgnoredRouteMatcher(new RouteTrieNode());
    private final RouteTrieNode root;

    private IgnoredRouteMatcher(RouteTrieNode root) {
        this.root = root;
    }

    /**
     * 判断给定路由是否命中某条忽略规则.
     * 只要遍历过程中命中终止节点, 即表示当前路由应被忽略.
     */
    public boolean matches(Route route) {
        RouteTrieNode current = root;
        for (int i = 0; i < route.length(); i++) {
            current = current.children.get(route.getRouteElement(i).key());
            if (current == null) {
                return false;
            }
            if (current.terminal) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final RouteTrieNode root = new RouteTrieNode();
        private boolean hasRoutes; // 标记当前是否至少注册过一条忽略路由

        /**
         * 添加一条忽略路由.
         * 当某个节点已被标记为终止节点时, 其更深层子路径无需再单独保留.
         */
        public Builder add(Route route) {
            RouteTrieNode current = root;
            for (int i = 0; i < route.length(); i++) {
                // 如果已经存在 "a.b", 再次添加 "a.b.c" 时, 应当被忽略.
                if (current.terminal) {
                    hasRoutes = true;
                    return this;
                }
                Object key = route.getRouteElement(i).key();
                current = current.children.computeIfAbsent(key, ignored -> new RouteTrieNode());
            }
            current.terminal = true;
            current.children.clear();
            hasRoutes = true;
            return this;
        }

        // 批量添加多条忽略路由
        public Builder add(Collection<Route> routes) {
            for (Route route : routes) {
                this.add(route);
            }
            return this;
        }

        public IgnoredRouteMatcher build() {
            return hasRoutes ? new IgnoredRouteMatcher(root) : EMPTY;
        }
    }

    private static final class RouteTrieNode {
        private final Map<Object, RouteTrieNode> children = new HashMap<>();
        private boolean terminal; // 标记当前节点是否对应一条完整的忽略路由
    }
}
