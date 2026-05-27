package net.momirealms.sparrow.yaml.route.element;

public class IndexElement implements RouteElement<Integer> {
    public final int key;

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
        return key;
    }

}
