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
     * 如果传入的值是 RouteElement, 那么会保留其显式节点类型;
     * 否则, 都会被进行toString, 然后识别为 KeyElement;
     * @param routeKeys 路由节点
     */
    public Route(@NotNull Object... routeKeys) {
        Objects.requireNonNull(routeKeys, "用于路由定位的节点数组不能为 null !");
        this.routeElements = new RouteElement<?>[routeKeys.length];
        this.routeKeys = new Object[routeKeys.length];
        for (int i = 0; i < routeKeys.length; i++) {
            RouteElement<?> routeElement = normalizeRouteElement(routeKeys[i]);
            this.routeElements[i] = routeElement;
            this.routeKeys[i] = routeElement.key();
        }
    }

    /**
     * 仅使用一个节点构造一条路由；
     * @param routeKey 路由节点
     */
    public Route(@NotNull Object routeKey) {
        this(new Object[] {routeKey});
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
     * 将点分路径拆成 mapping key 路由, 空路径段会被保留.
     *
     * @param path 非空的点分路径
     * @return 由每个 String 路径段组成的路由
     * @throws IllegalArgumentException 当 path 是空字符串时
     */
    @NotNull
    public static Route dotted(@NotNull String path) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("用于路由定位的点分路径不能为空!");
        }
        return Route.from((Object[]) path.split("\\.", -1));
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
     * 返回规范化后的路由 key 副本, 显式 RouteElement 会拆成其中的 key.
     * @return 路由 key 数组
     */
    @NotNull
    public Object[] routeKeys() {
        return this.routeKeys.clone();
    }

    /**
     * 返回路径的长度.
     * @return 路径长度
     */
    public int length() {
        return this.routeElements.length;
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
        return this.routeElements[i];
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
        if (i < 0 || i >= this.length()) {
            return null;
        }
        return this.routeElements[i];
    }

    /**
     * 返回当前路由节点的最后一个路由元素;
     * @return 路由元素
     */
    @NotNull
    public RouteElement<?> getLastRouteElement() {
        return this.routeElements[this.routeElements.length - 1];
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
        return this.routeKeys[i];
    }

    /**
     * 返回路由节点的第一个元素;
     * @return 索引位置的路由节点元素
     */
    @NotNull
    public RouteElement<?> getFirstRouteElement() {
        return this.routeElements[0];
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
        Object[] newElements = Arrays.copyOf(this.routeElements, this.length() + route.length(), Object[].class);
        System.arraycopy(route.routeElements, 0, newElements, this.length(), route.length());
        return new Route(newElements);
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
        Object[] newElements = Arrays.copyOf(this.routeElements, this.length() + 1, Object[].class);
        newElements[newElements.length - 1] = key;
        return new Route(newElements);
    }

    /**
     * 返回该路由的父路由.
     * 创建一个新路由并复制该路由的数组（不包含最后一个节点）.
     * 空路由或单节点路由没有父路由, 调用此方法返回 null;
     * @return 新的 Route 对象
     */
    @Nullable
    public Route parent() {
        if (this.routeElements.length <= 1) return null;
        Object[] parentElements = Arrays.copyOf(this.routeElements, this.routeElements.length - 1, Object[].class);
        return new Route(parentElements);
    }

    /**
     * 检查当前路由是否是传入路由的父路由.
     * @param subRoute 需要检查的路由.
     */
    public boolean isParentRouteOf(Route subRoute) {
        if (this.length() >= subRoute.length()) return false;
        for (int i = 0; i < this.length(); i++) {
            if (!Objects.equals(this.getRouteElement(i), subRoute.getRouteElement(i))) {
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
            if (!Objects.equals(this.getRouteElement(i), parentRoute.getRouteElement(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回用于异常和日志的路径名称, null 显示为 {@code <unknown>}, 空路由显示为 {@code <root>}.
     *
     * @param path 路由
     * @return 可读路径名称
     */
    public static String pathName(@Nullable Route path) {
        if (path == null) return "<unknown>";
        if (path.length() == 0) return "<root>";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            RouteElement<?> routeElement = path.getRouteElement(i);
            if (routeElement instanceof IndexElement indexElement) {
                builder.append('[').append(indexElement.key()).append(']');
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(routeElement.key());
        }
        return "\"" + builder + "\"";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Route route)) return false;
        return Arrays.equals(this.routeElements, route.routeElements);
    }

    @Override
    public int hashCode() {
        if (this.length() == 0) return 0;
        return this.length() == 1 ? this.routeElements[0].hashCode() : Arrays.hashCode(this.routeElements);
    }

    @Override
    public String toString() {
        return "Route{" +
                "route=" + Arrays.toString(this.routeKeys) +
                '}';
    }

    @NotNull
    private static RouteElement<?> normalizeRouteElement(@NotNull Object routeKey) {
        Objects.requireNonNull(routeKey, "用于路由定位的数组中不可包含 null 节点!");
        if (routeKey instanceof RouteElement<?> routeElement) {
            Objects.requireNonNull(routeElement.key(), "用于路由定位的数组中的 RouteElement 元素的值不可为 null !");
            return routeElement;
        }
        if (routeKey instanceof Integer integer && integer >= 0) {
            return new IndexElement(integer);
        }
        return new KeyElement(routeKey.toString());
    }
}
