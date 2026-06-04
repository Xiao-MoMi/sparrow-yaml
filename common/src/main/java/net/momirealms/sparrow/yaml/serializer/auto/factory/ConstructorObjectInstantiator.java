package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;

import java.lang.reflect.Constructor;

final class ConstructorObjectInstantiator implements ObjectInstantiator {
    private final Constructor<?> constructor;

    ConstructorObjectInstantiator(Constructor<?> constructor) {
        this.constructor = constructor;
        this.constructor.trySetAccessible();
    }

    @Override
    public Object instantiate(Object[] arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new AutoSerializerException("Cannot instantiate "
                    + constructor.getDeclaringClass().getName(), e);
        }
    }
}
