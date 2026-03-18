package net.momirealms.sparrow.yaml.route.element;

public class KeyElement implements RouteElement<Object> {
    public final Object key;

    public KeyElement(Object key) {
        this.key = key;
    }

    @Override
    public Object key() {
        return key;
    }
}
