package net.momirealms.sparrow.yaml.serializer.builder;

public final class NodeSerializerFunctions {

    private NodeSerializerFunctions() {
    }

    @FunctionalInterface
    public interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    public interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    public interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    public interface Function6<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }

    @FunctionalInterface
    public interface Function7<A, B, C, D, E, F, G, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g);
    }

    @FunctionalInterface
    public interface Function8<A, B, C, D, E, F, G, H, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h);
    }

    @FunctionalInterface
    public interface Function9<A, B, C, D, E, F, G, H, I, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i);
    }

    @FunctionalInterface
    public interface Function10<A, B, C, D, E, F, G, H, I, J, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j);
    }

    @FunctionalInterface
    public interface Function11<A, B, C, D, E, F, G, H, I, J, K, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k);
    }

    @FunctionalInterface
    public interface Function12<A, B, C, D, E, F, G, H, I, J, K, L, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k, L l);
    }

    @FunctionalInterface
    public interface Function13<A, B, C, D, E, F, G, H, I, J, K, L, M, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k, L l, M m);
    }

    @FunctionalInterface
    public interface Function14<A, B, C, D, E, F, G, H, I, J, K, L, M, N, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k, L l, M m, N n);
    }

    @FunctionalInterface
    public interface Function15<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k, L l, M m, N n, O o);
    }

    @FunctionalInterface
    public interface Function16<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k, L l, M m, N n, O o, P p);
    }
}
