package net.momirealms.sparrow.yaml.route.element;

public interface RouteElement<T> {

    /**
     * 获取当前路由元素的路由节点值;
     * @return 节点值;
     */
    T key();

}
