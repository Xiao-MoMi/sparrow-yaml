package net.momirealms.sparrow.yaml.route;

import net.momirealms.sparrow.yaml.route.element.IndexElement;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import net.momirealms.sparrow.yaml.route.element.RouteElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class Route {
    private static final Route EMPTY = new Route(new Object[0]);

    @NotNull
    private final RouteElement<?>[] routeElements;
    @NotNull
    private final Object[] routeKeys;

    /**
     * 获取一个空路由
     * @return 空路由
     */
    public static Route empty() {
        return EMPTY;
    }

    /**
     * 构造一个多路由.
     * 如果传入的值是 Integer 且 >= 0, 那么会被识别为 IndexElement;
     * 如果传入的值是 RouteElement, 那么会直接作为路由节点;
     * 否则, 都会被进行toString, 然后识别为 KeyElement;
     * @param routeKeys 路由节点
     */
    public Route(@NotNull Object... routeKeys) {
        this.routeKeys = routeKeys.clone();
        if (this.routeKeys.length == 0) {
            this.routeElements = new RouteElement[0];
            return;
        }
        for (Object key : this.routeKeys) {
            Objects.requireNonNull(key, "用于路由定位的数组中不可包含 null 节点!");
        }
        this.routeElements = Arrays.stream(this.routeKeys)
                .map(it -> {
                    if (it instanceof Integer integer && integer >= 0) {
                        return new IndexElement(integer);
                    }
                    if (it instanceof RouteElement<?> routeElement) {
                        Objects.requireNonNull(routeElement.key(), "用于路由定位的数组中的 RouteElement 元素的值不可为 null !");
                        return routeElement;
                    }
                    return new KeyElement(it.toString());
                })
                .toArray(RouteElement[]::new);
    }

    /**
     * 仅使用一个节点构造一条路由；
     * @param routeKey 路由节点
     */
    public Route(@NotNull Object routeKey) {
        this.routeKeys = new Object[] { Objects.requireNonNull(routeKey, "路由的节点不可包含 null !") };
        this.routeElements = new RouteElement[] {
                routeKey instanceof Integer integer ?
                        new IndexElement(integer) :
                        new KeyElement(routeKey.toString())
        };
    }

    /**
     * 从给定的非空键构造一条路由。
     * 注意: 给定的键必须是不可变的!
     * @param route 路由键
     * @return 路由对象
     */
    @NotNull
    public static Route from(@NotNull Object... route) {
        if (Objects.requireNonNull(route, "用于路由定位的节点数组不能为 null !").length == 0)
            throw new IllegalArgumentException("用于路由定位的节点数组不能为空!");
        return new Route(route);
    }

    /**
     * 给定一个路由对象, 为其在尾部添加新的路由节点;
     * 如果路由对象为空, 则创建一个新的节点;
     * @param route 需要添加的路由对象
     * @param key   尾部新增的路由节点
     * @return 全新的路由对象
     * @see Route#add(Object)
     */
    @NotNull
    public static Route addTo(@Nullable Route route, @NotNull Object key) {
        return route == null ? new Route(key) : route.add(key);
    }

    /**
     * 返回当前路由对象的路由节点数组;
     * @return 路由节点数组
     */
    @NotNull
    public Object[] routeKeys() {
        return routeKeys.clone();
    }

    /**
     * 返回路径的长度.
     * @return 路径长度
     */
    public int length() {
        return routeElements.length;
    }

    /**
     * 返回给定索引处的路由节点的 RouteElement 。
     * 始终验证请求的索引是否越界, 如果越界, 调用此方法将抛出 {@link ArrayIndexOutOfBoundsException};
     * {@link #length() length}.
     *
     * @param i 目标索引
     * @return 索引位置的路由节点的值
     */
    @NotNull
    public RouteElement<?> getRouteElement(int i) {
        return routeElements[i];
    }

    /**
     * 返回给定索引处的路由节点的 RouteElement 。
     * 如果请求的索引越界, 调用此方法将返回 Null ;
     * {@link #length() length}.
     *
     * @param i 目标索引
     * @return 索引位置的路由节点的值
     */
    @Nullable
    public RouteElement<?> getRouteElementOrNull(int i) {
        if (i > length() - 1) {
            return null;
        }
        return routeElements[i];
    }

    /**
     * 返回当前路由节点的最后一个路由元素;
     * @return 路由元素
     */
    @NotNull
    public RouteElement<?> getLastRouteElement() {
        return routeElements[routeElements.length - 1];
    }

    /**
     * 返回给定索引处的路由节点的 Key 。
     * 始终验证请求的索引是否越界, 如果越界, 调用此方法将抛出 {@link ArrayIndexOutOfBoundsException};
     * {@link #length() length}.
     * @param i 目标索引
     * @return 索引位置的路由节点的值
     */
    @NotNull
    public Object getRouteKey(int i) {
        return routeKeys[i];
    }

    /**
     * 返回路由节点的第一个元素;
     * @return 索引位置的路由节点元素
     */
    @NotNull
    public RouteElement<?> getFirstRouteElement() {
        return routeElements[0];
    }

    /**
     * 将给定的路由添加到当前路由的末尾, 并返回新的路由对象.
     * 如果给定的路由是null, 那么将会返回它自身;
     * @param route 目标路由;
     * @return Route 对象
     */
    @NotNull
    public Route add(@Nullable Route route) {
        if (route == null) {
            return this;
        }
        Object[] newKeys = Arrays.copyOf(this.routeKeys, this.length() + route.length());
        System.arraycopy(route.routeKeys, 0, newKeys, this.length(), route.length());
        return new Route(newKeys);
    }

    /**
     * 将给定的非空键添加到路由末尾并返回新的路由对象.
     * 给定的 key 支持 RouteElement ;
     * 例如: 如果将键 1 添加到路由["a", "b"] ，则生成的路由将由["a", "b", 1]表示。
     * @param key 在末尾添加的路由节点
     * @return 新的 Route 对象
     */
    @NotNull
    public Route add(@NotNull Object key) {
        Objects.requireNonNull(key, "路由的节点的值不可包含 null !");

        Object[] newKeys;
        if (key instanceof RouteElement<?> element) {
            Objects.requireNonNull(element.key(), "路由的节点的值不可包含 null !");
            newKeys = Arrays.copyOf(this.routeKeys, this.length() + 1);
            newKeys[newKeys.length - 1] = element.key();
        } else {
            newKeys = Arrays.copyOf(this.routeKeys, this.length() + 1);
            newKeys[newKeys.length - 1] = key;
        }

        return new Route(newKeys);
    }

    /**
     * 返回该路由的父路由.
     * 创建一个新路由并复制该路由的数组（不包含最后一个节点）.
     * 如果 <code>{@link #length()} == 1</code> 的路由调用此方法将返回 Null;
     * @return 新的 Route 对象
     */
    @Nullable
    public Route parent() {
        if (routeElements.length == 1) return null;
        if (routeElements.length == 2) return Route.from(routeKeys[0]);
        return Route.from(Arrays.copyOf(routeKeys, routeKeys.length - 1));
    }

    /**
     * 检查当前路由是否是传入路由的父路由.
     * @param subRoute 需要检查的路由.
     */
    public boolean isParentRouteOf(Route subRoute) {
        if (this.length() >= subRoute.length()) return false;
        for (int i = 0; i < this.length(); i++) {
            if (!Objects.equals(this.getRouteElement(i).key(), subRoute.getRouteElement(i).key())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查当前路由是否是传入路由的子路由.
     * @param parentRoute 需要检查的路由.
     */
    public boolean isSubRouteOf(Route parentRoute) {
        if (this.length() <= parentRoute.length()) return false;
        for (int i = 0; i < parentRoute.length(); i++) {
            if (!Objects.equals(this.getRouteElement(i).key(), parentRoute.getRouteElement(i).key())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Route route1)) return false;
        if (this.length() != route1.length()) return false;
        if (this.length() == 1 && route1.length() == 1) return Objects.equals(this.getRouteKey(0), route1.getRouteKey(0));
        return Arrays.equals(routeKeys, route1.routeKeys);
    }

    @Override
    public int hashCode() {
        if (length() == 0) return 0;
        return length() > 1 ? Arrays.hashCode(routeKeys) : Objects.hashCode(routeKeys[0]);
    }

    @Override
    public String toString() {
        return "Route{" +
                "route=" + Arrays.toString(routeKeys) +
                '}';
    }
}
