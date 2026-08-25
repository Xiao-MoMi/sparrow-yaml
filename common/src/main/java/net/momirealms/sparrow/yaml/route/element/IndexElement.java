package net.momirealms.sparrow.yaml.route.element;

/**
 * 表示 YAML sequence 下标的路由元素.
 */
public class IndexElement implements RouteElement<Integer> {
    /** 非负 sequence 下标. */
    public final int key;

    /**
     * 创建 sequence 下标路由元素.
     *
     * @param key 非负下标
     * @throws IllegalArgumentException 当下标小于 0 时
     */
    public IndexElement(int key) {
        if (key < 0) {
            throw new IllegalArgumentException("索引不支持小于 0 的整数!");
        }
        this.key = key;
    }

    public static IndexElement of(int key) {
        return new IndexElement(key);
    }

    @Override
    public Integer key() {
        return this.key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexElement that)) return false;
        return this.key == that.key;
    }

    @Override
    public int hashCode() {
        return 31 * IndexElement.class.hashCode() + Integer.hashCode(this.key);
    }

}
