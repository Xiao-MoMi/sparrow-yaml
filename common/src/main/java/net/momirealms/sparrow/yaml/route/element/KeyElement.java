package net.momirealms.sparrow.yaml.route.element;

import java.util.Objects;

/**
 * 表示 YAML mapping key 的路由元素.
 */
public class KeyElement implements RouteElement<Object> {
    /** mapping key. */
    public final Object key;

    /**
     * 创建 mapping key 路由元素.
     *
     * @param key mapping key
     */
    public KeyElement(Object key) {
        this.key = key;
    }

    @Override
    public Object key() {
        return this.key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyElement that)) return false;
        return Objects.equals(this.key, that.key);
    }

    @Override
    public int hashCode() {
        return 31 * KeyElement.class.hashCode() + Objects.hashCode(this.key);
    }
}
