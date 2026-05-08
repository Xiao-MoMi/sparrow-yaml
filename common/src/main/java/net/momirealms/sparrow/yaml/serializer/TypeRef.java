package net.momirealms.sparrow.yaml.serializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 用于在运行时获取泛型类型信息的工具类.
 * @param <T> 需要保留的泛型类型
 */
public abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        // 通过反射获取超类的泛型参数, 并提取出实际的类型信息
        Type superClass = getClass().getGenericSuperclass();
        if (!(superClass instanceof ParameterizedType parameterizedType)) {
            throw new IllegalStateException("TypeRef must be created with generic type information");
        }
        this.type = parameterizedType.getActualTypeArguments()[0];
    }

    // 获取解析出的实际类型信息
    public final Type type() {
        return this.type;
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof TypeRef<?> typeRef)) return false;
        return Objects.equals(type, typeRef.type);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public final String toString() {
        return type.getTypeName();
    }
}
