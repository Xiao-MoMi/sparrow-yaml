package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerContext;
import net.momirealms.sparrow.yaml.serializer.auto.accessor.FieldAccessor;
import net.momirealms.sparrow.yaml.serializer.auto.accessor.FieldAccessors;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.serializer.auto.resolver.ReflectionTypeSerializerResolver;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 基于 ASM 动态生成 NodeSerializer 实现的自动序列化器工厂.
 */
public class AsmAutoSerializerFactory implements AutoSerializerFactory {

    /**
     * 创建新的 ASM 自动序列化器, 并为本次创建过程建立递归解析上下文.
     */
    @Override
    public <T> NodeSerializer<T> create(
            java.lang.reflect.Type type,
            SerializerRegistry registry,
            AutoSerializerBinding binding
    ) {
        AutoSerializerContext ctx = new AutoSerializerContext(registry, new ReflectionTypeSerializerResolver(this));
        return createInternal(type, ctx, binding);
    }

    /**
     * 在共享上下文中创建序列化器, 用于支持嵌套对象和递归结构解析.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> createInternal(
            java.lang.reflect.Type type,
            AutoSerializerContext ctx,
            @Nullable AutoSerializerBinding binding
    ) {
        // 规范化类型, 并优先复用注册表中已有的序列化器.
        java.lang.reflect.Type resolved = TypeUtils.normalize(type, Map.of());
        NodeSerializer<T> existing = ctx.getRegistry().get(resolved);
        if (existing != null) {
            return existing;
        }
        if (ctx.isResolving(resolved)) {
            return NodeSerializer.lazy(() -> ctx.getRegistry().get(resolved));
        }

        // 校验目标类型是否支持自动生成
        ctx.pushResolving(resolved);
        Class<T> rawType = (Class<T>) TypeUtils.rawType(resolved);
        validateSupportedType(rawType);

        // 解析类元数据和字段泛型绑定
        byte[] classBytes = readClassBytes(rawType);
        ClassMeta meta = AsmClassMetaParser.parse(rawType, classBytes, binding);
        Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables = TypeUtils.typeVariables(rawType, resolved);
        meta.fields = collectHierarchyFields(rawType, typeVariables);
        List<ClassMeta.FieldBinding> allBindings = collectFieldBindings(meta, ctx, typeVariables);

        // 选择实例化策略, record 和普通 class 分开处理.
        AsmSerializerGenerator.ConstructorChoice constructorChoice;
        if (rawType.isRecord()) {
            constructorChoice = recordCtor(rawType, ctx, allBindings, typeVariables);
        } else {
            constructorChoice = classCtor(rawType, ctx, binding, allBindings, typeVariables);
        }

        // 生成并加载 serializer 类.
        List<ClassMeta.FieldBinding> fieldBindings = collectConcreteFieldBindings(allBindings);
        byte[] generatedBytes = AsmSerializerGenerator.generate(
                meta,
                allBindings,
                fieldBindings,
                constructorChoice,
                Type.getDescriptor(rawType)
        );
        NodeSerializer<T> serializer = loadGeneratedSerializer(rawType, generatedBytes, allBindings);

        // 注册生成结果, 并结束当前递归解析状态.
        ctx.popResolving(resolved);
        ctx.getRegistry().registerUnsafe(resolved, serializer);
        return serializer;
    }

    /**
     * 校验目标类型是否适合通过 ASM 自动生成序列化器.
     */
    private static void validateSupportedType(Class<?> rawType) {
        if (rawType.isInterface()) {
            throw new AutoSerializerException("No serializer for interface " + rawType.getName());
        }
        if (Modifier.isAbstract(rawType.getModifiers()) && !rawType.isEnum()) {
            throw new AutoSerializerException("No serializer for abstract " + rawType.getName());
        }
        if (rawType.isArray()) {
            throw new AutoSerializerException("No serializer for array " + rawType.getName());
        }
    }

    /**
     * 为可序列化字段创建 FieldBinding, 并解析每个字段对应的 NodeSerializer.
     */
    private static List<ClassMeta.FieldBinding> collectFieldBindings(
            ClassMeta meta,
            AutoSerializerContext ctx,
            Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables
    ) {
        List<ClassMeta.FieldBinding> bindings = new ArrayList<>();
        int serializerIndex = 0;

        for (int i = 0; i < meta.fields.size(); i++) {
            ClassMeta.FieldEntry field = meta.fields.get(i);
            if (field.hasYamlIgnore) {
                continue;
            }

            Map<TypeVariable<?>, java.lang.reflect.Type> fieldVariables = field.typeVariables != null ? field.typeVariables : typeVariables;
            java.lang.reflect.Type fieldType = TypeUtils.normalize(field.genericType, fieldVariables);
            bindings.add(new ClassMeta.FieldBinding(field, ctx.resolve(fieldType), serializerIndex++, bindings.size()));
        }

        return bindings;
    }

    /**
     * 收集父类优先的字段元数据, 并解析每一层泛型父类绑定.
     */
    private static List<ClassMeta.FieldEntry> collectHierarchyFields(
            Class<?> rawType,
            Map<TypeVariable<?>, java.lang.reflect.Type> variables
    ) {
        List<ClassMeta.FieldEntry> result = new ArrayList<>();
        Map<String, Field> fieldNames = new LinkedHashMap<>();
        for (ClassFieldContext fieldContext : classHierarchy(rawType, variables)) {
            List<ClassMeta.FieldEntry> fields = AsmClassMetaParser.parseFields(
                    fieldContext.type(),
                    readClassBytes(fieldContext.type()),
                    fieldContext.variables()
            );
            for (int i = 0; i < fields.size(); i++) {
                ClassMeta.FieldEntry field = fields.get(i);
                if (!field.hasYamlIgnore) {
                    String name = field.hasYamlProperty ? field.yamlPropertyValue : field.name;
                    Field previous = fieldNames.putIfAbsent(name, field.reflectiveField);
                    if (previous != null) {
                        throw duplicateYamlField(rawType, name, previous, field.reflectiveField);
                    }
                }
                result.add(field);
            }
        }
        return result;
    }

    private static List<ClassFieldContext> classHierarchy(Class<?> rawType, Map<TypeVariable<?>, java.lang.reflect.Type> variables) {
        LinkedList<ClassFieldContext> result = new LinkedList<>();
        Class<?> currentType = rawType;
        Map<TypeVariable<?>, java.lang.reflect.Type> currentVariables = variables;
        while (currentType != null && currentType != Object.class && currentType != Record.class) {
            result.addFirst(new ClassFieldContext(currentType, Map.copyOf(currentVariables)));
            java.lang.reflect.Type genericSuperType = currentType.getGenericSuperclass();
            if (genericSuperType instanceof ParameterizedType parameterizedSuperType) {
                Class<?> superType = TypeUtils.rawType(parameterizedSuperType);
                currentVariables = resolveSuperVariables(superType, parameterizedSuperType, currentVariables);
                currentType = superType;
            } else {
                currentType = currentType.getSuperclass();
                currentVariables = Map.of();
            }
        }
        return result;
    }

    private static Map<TypeVariable<?>, java.lang.reflect.Type> resolveSuperVariables(
            Class<?> superType,
            ParameterizedType parameterizedSuperType,
            Map<TypeVariable<?>, java.lang.reflect.Type> childVariables
    ) {
        TypeVariable<?>[] variables = superType.getTypeParameters();
        java.lang.reflect.Type[] arguments = parameterizedSuperType.getActualTypeArguments();
        Map<TypeVariable<?>, java.lang.reflect.Type> result = new HashMap<>(Math.max((int) (variables.length / 0.75f) + 1, 16));
        for (int i = 0; i < variables.length; i++) {
            result.put(variables[i], TypeUtils.normalize(arguments[i], childVariables));
        }
        return result;
    }

    private static AutoSerializerException duplicateYamlField(Class<?> rawType, String name, Field previous, Field current) {
        return new AutoSerializerException("Duplicate YAML field '" + name + "' while auto-serializing "
                + rawType.getName() + ": "
                + previous.getDeclaringClass().getName() + "." + previous.getName()
                + " conflicts with "
                + current.getDeclaringClass().getName() + "." + current.getName());
    }

    private record ClassFieldContext(Class<?> type, Map<TypeVariable<?>, java.lang.reflect.Type> variables) {
    }

    /**
     * 收集真实字段绑定, 排除构造器参数专用的合成绑定.
     */
    private static List<ClassMeta.FieldBinding> collectConcreteFieldBindings(List<ClassMeta.FieldBinding> bindings) {
        List<ClassMeta.FieldBinding> fields = new ArrayList<>();
        for (int i = 0; i < bindings.size(); i++) {
            ClassMeta.FieldBinding binding = bindings.get(i);
            if (binding.field != null && binding.field.reflectiveField != null) {
                fields.add(binding);
            }
        }
        return fields;
    }

    /**
     * 选择普通 class 的构造器调用策略.
     */
    private <T> AsmSerializerGenerator.ConstructorChoice classCtor(
            Class<T> rawType,
            AutoSerializerContext ctx,
            @Nullable AutoSerializerBinding binding,
            List<ClassMeta.FieldBinding> bindings,
            Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables
    ) {
        // 外部绑定优先级最高.
        AutoSerializerBinding.ConstructorBinding externalBinding = binding != null ? binding.constructorBinding() : null;
        if (externalBinding != null) {
            return extBinding(rawType, ctx, externalBinding, bindings);
        }

        // 其次选择 @YamlConstructor 标注的构造器.
        Constructor<?>[] constructors = rawType.getDeclaredConstructors();
        Constructor<?> annotatedConstructor = null;
        for (int i = 0; i < constructors.length; i++) {
            Constructor<?> constructor = constructors[i];
            if (constructor.isAnnotationPresent(YamlConstructor.class)) {
                if (annotatedConstructor != null) {
                    throw new AutoSerializerException("Multiple @YamlConstructor in " + rawType.getName());
                }
                annotatedConstructor = constructor;
            }
        }
        if (annotatedConstructor != null) {
            return annBinding(rawType, ctx, annotatedConstructor, bindings, typeVariables);
        }

        // 如果存在无参构造器, 使用无参构造 + 字段注入.
        try {
            rawType.getDeclaredConstructor();
            return AsmSerializerGenerator.ConstructorChoice.NO_ARGS;
        } catch (NoSuchMethodException ignored) {
        }

        // 唯一构造器可通过参数名自动绑定.
        if (constructors.length == 1) {
            return singleBinding(rawType, ctx, constructors[0], bindings, typeVariables);
        }

        throw new AutoSerializerException("No suitable constructor for " + rawType.getName());
    }

    /**
     * 为 record 创建全参 canonical constructor 的调用策略.
     */
    private <T> AsmSerializerGenerator.ConstructorChoice recordCtor(
            Class<T> rawType,
            AutoSerializerContext ctx,
            List<ClassMeta.FieldBinding> bindings,
            Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables
    ) {
        RecordComponent[] components = rawType.getRecordComponents();
        if (components == null || components.length == 0) {
            return AsmSerializerGenerator.ConstructorChoice.NO_ARGS;
        }

        List<String> keys = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // 按 record 组件顺序构造参数绑定.
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String descriptor = Type.getType(component.getType()).getDescriptor();
            if (component.isAnnotationPresent(YamlIgnore.class)) {
                keys.add(null);
                indices.add(appendStub(bindings, descriptor));
                continue;
            }

            YamlProperty property = component.getAnnotation(YamlProperty.class);
            keys.add(property != null ? property.value() : component.getName());

            java.lang.reflect.Type parameterType = TypeUtils.normalize(component.getGenericType(), typeVariables);
            indices.add(appendSer(bindings, ctx.resolve(parameterType), descriptor));
        }

        return new AsmSerializerGenerator.ConstructorChoice(true, keys, indices);
    }

    /**
     * 根据外部 ConstructorBinding 创建构造器参数绑定.
     */
    private <T> AsmSerializerGenerator.ConstructorChoice extBinding(
            Class<T> rawType,
            AutoSerializerContext ctx,
            AutoSerializerBinding.ConstructorBinding binding,
            List<ClassMeta.FieldBinding> bindings
    ) {
        try {
            rawType.getDeclaredConstructor(binding.parameterTypes());
        } catch (NoSuchMethodException e) {
            throw new AutoSerializerException("External ctor not found: " + rawType.getName(), e);
        }

        List<Integer> indices = new ArrayList<>();
        Class<?>[] parameterTypes = binding.parameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            java.lang.reflect.Type parameterType = TypeUtils.normalize(parameterTypes[i], Map.of());
            String descriptor = Type.getType(parameterTypes[i]).getDescriptor();
            indices.add(appendSer(bindings, ctx.resolve(parameterType), descriptor));
        }

        return new AsmSerializerGenerator.ConstructorChoice(true, binding.parameterNames(), indices);
    }

    /**
     * 根据 @YamlConstructor 构造器创建参数绑定.
     */
    private <T> AsmSerializerGenerator.ConstructorChoice annBinding(
            Class<T> rawType,
            AutoSerializerContext ctx,
            Constructor<?> constructor,
            List<ClassMeta.FieldBinding> bindings,
            Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables
    ) {
        Parameter[] parameters = constructor.getParameters();
        List<String> keys = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // 按构造器参数顺序解析 YAML 键名和序列化器.
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            YamlProperty property = parameter.getAnnotation(YamlProperty.class);
            String key = property != null ? property.value() : (parameter.isNamePresent() ? parameter.getName() : null);
            if (key == null || key.isBlank()) {
                throw new AutoSerializerException("No name for ctor param " + keys.size() + " in " + rawType.getName());
            }

            keys.add(key);

            java.lang.reflect.Type parameterType = TypeUtils.normalize(parameter.getParameterizedType(), typeVariables);
            String descriptor = Type.getType(parameter.getType()).getDescriptor();
            indices.add(appendSer(bindings, ctx.resolve(parameterType), descriptor));
        }

        return new AsmSerializerGenerator.ConstructorChoice(true, keys, indices);
    }

    /**
     * 使用唯一构造器及其保留的参数名创建参数绑定.
     */
    private <T> AsmSerializerGenerator.ConstructorChoice singleBinding(
            Class<T> rawType,
            AutoSerializerContext ctx,
            Constructor<?> constructor,
            List<ClassMeta.FieldBinding> bindings,
            Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables
    ) {
        Parameter[] parameters = constructor.getParameters();
        List<String> keys = new ArrayList<>();

        // 先校验所有参数名存在, 避免生成一半后才失败.
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (!parameter.isNamePresent()) {
                throw new AutoSerializerException("No param names in " + rawType.getName());
            }
            keys.add(parameter.getName());
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            java.lang.reflect.Type parameterType = TypeUtils.normalize(parameter.getParameterizedType(), typeVariables);
            String descriptor = Type.getType(parameter.getType()).getDescriptor();
            indices.add(appendSer(bindings, ctx.resolve(parameterType), descriptor));
        }

        return new AsmSerializerGenerator.ConstructorChoice(true, keys, indices);
    }

    /**
     * 添加构造器参数专用的合成绑定.
     */
    private int appendSer(
            List<ClassMeta.FieldBinding> bindings,
            NodeSerializer<?> serializer,
            String descriptor
    ) {
        int index = bindings.size();
        ClassMeta.FieldEntry stub = new ClassMeta.FieldEntry();
        stub.name = "_ctor$" + index;
        stub.descriptor = descriptor;

        bindings.add(new ClassMeta.FieldBinding(stub, serializer, index));
        return index;
    }

    /**
     * 添加被忽略 record 组件的占位绑定.
     */
    private int appendStub(List<ClassMeta.FieldBinding> bindings, String descriptor) {
        int index = bindings.size();
        ClassMeta.FieldEntry stub = new ClassMeta.FieldEntry();
        stub.name = "_ignored$" + index;
        stub.descriptor = descriptor;

        bindings.add(new ClassMeta.FieldBinding(stub, null, index));
        return index;
    }

    /**
     * 将生成的 serializer 字节码定义到目标类 nest host 的 Lookup 中.
     */
    @SuppressWarnings("unchecked")
    private <T> NodeSerializer<T> loadGeneratedSerializer(
            Class<T> rawType,
            byte[] generatedBytes,
            List<ClassMeta.FieldBinding> bindings
    ) {
        try {
            // 内部类需要定义到最外层 nest host, 才能访问同一 nest 的私有成员.
            Class<?> nestHost = rawType;
            while (nestHost.getEnclosingClass() != null) {
                nestHost = nestHost.getEnclosingClass();
            }

            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(nestHost, MethodHandles.lookup());
            Class<?> generatedClass = lookup.defineClass(generatedBytes);

            NodeSerializer<?>[] serializers = new NodeSerializer<?>[bindings.size()];
            FieldAccessor[] accessors = new FieldAccessor[countAccessors(bindings)];
            for (int i = 0; i < bindings.size(); i++) {
                ClassMeta.FieldBinding binding = bindings.get(i);
                serializers[binding.index] = binding.serializer;
                if (binding.accessorIndex >= 0 && binding.field.reflectiveField != null) {
                    accessors[binding.accessorIndex] = FieldAccessors.of(binding.field.reflectiveField);
                }
            }

            MethodHandle constructor = lookup.findConstructor(
                    generatedClass,
                    MethodType.methodType(void.class, NodeSerializer[].class, FieldAccessor[].class)
            );
            return (NodeSerializer<T>) constructor.invoke(serializers, accessors);
        } catch (Throwable e) {
            throw new AutoSerializerException("Cannot load generated serializer for " + rawType.getName(), e);
        }
    }

    private static int countAccessors(List<ClassMeta.FieldBinding> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            ClassMeta.FieldBinding binding = bindings.get(i);
            if (binding.accessorIndex >= 0 && binding.field.reflectiveField != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 从 ClassLoader 资源中读取目标类的原始字节码.
     */
    private static byte[] readClassBytes(Class<?> clazz) {
        String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream inputStream = clazz.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new AutoSerializerException("No class bytes: " + clazz.getName());
            }
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new AutoSerializerException("IO error reading " + clazz.getName(), e);
        }
    }
}
