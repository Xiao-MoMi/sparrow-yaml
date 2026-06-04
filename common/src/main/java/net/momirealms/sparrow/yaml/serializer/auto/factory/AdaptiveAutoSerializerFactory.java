package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * Uses ASM when the optional runtime dependency is present, otherwise falls back to reflection.
 */
public final class AdaptiveAutoSerializerFactory implements AutoSerializerFactory {
    private final AutoSerializerFactory reflectionFactory = new ReflectionAutoSerializerFactory();
    private volatile AutoSerializerFactory asmFactory;
    private volatile boolean asmUnavailable;

    @Override
    public <T> NodeSerializer<T> create(Type type, SerializerRegistry registry, AutoSerializerBinding binding) {
        AutoSerializerFactory asm = asmFactoryOrNull();
        if (asm == null) {
            return reflectionFactory.create(type, registry, binding);
        }
        try {
            return asm.create(type, registry, binding);
        } catch (LinkageError e) {
            if (!isMissingAsm(e)) {
                throw e;
            }
            asmUnavailable = true;
            return reflectionFactory.create(type, registry, binding);
        }
    }

    @Override
    public <T> NodeSerializer<T> createInternal(Type type, AutoSerializerContext context, @Nullable AutoSerializerBinding binding) {
        AutoSerializerFactory asm = asmFactoryOrNull();
        if (asm == null) {
            return reflectionFactory.createInternal(type, context, binding);
        }
        try {
            return asm.createInternal(type, context, binding);
        } catch (LinkageError e) {
            if (!isMissingAsm(e)) {
                throw e;
            }
            asmUnavailable = true;
            return reflectionFactory.createInternal(type, context, binding);
        }
    }

    private AutoSerializerFactory asmFactoryOrNull() {
        if (asmUnavailable) {
            return null;
        }
        AutoSerializerFactory local = asmFactory;
        if (local != null) {
            return local;
        }
        try {
            local = new AsmAutoSerializerFactory();
            asmFactory = local;
            return local;
        } catch (LinkageError e) {
            if (!isMissingAsm(e)) {
                throw e;
            }
            asmUnavailable = true;
            return null;
        }
    }

    private static boolean isMissingAsm(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof NoClassDefFoundError || current instanceof ClassNotFoundException) {
                return true;
            }
        }
        return false;
    }
}
