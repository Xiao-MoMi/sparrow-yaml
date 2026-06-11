package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerContext;
import net.momirealms.sparrow.yaml.serializer.auto.parameter.ConstructorParameter;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import net.momirealms.sparrow.yaml.serializer.auto.member.FieldMember;
import net.momirealms.sparrow.yaml.serializer.auto.member.RecordMember;
import net.momirealms.sparrow.yaml.serializer.auto.member.SerializableMember;
import net.momirealms.sparrow.yaml.serializer.auto.plan.ConstructorPlan;
import net.momirealms.sparrow.yaml.serializer.auto.plan.FieldInjectionPlan;
import net.momirealms.sparrow.yaml.serializer.auto.plan.InstantiationPlan;
import net.momirealms.sparrow.yaml.serializer.auto.plan.UnsafeInstantiationPlan;
import net.momirealms.sparrow.yaml.serializer.auto.resolver.ReflectionTypeSerializerResolver;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

public class ReflectionAutoSerializerFactory implements AutoSerializerFactory {

    @Override
    public <T> NodeSerializer<T> create(Type type, SerializerRegistry registry, AutoSerializerBinding binding) {
        AutoSerializerContext context = new AutoSerializerContext(registry, new ReflectionTypeSerializerResolver(this));
        return createInternal(type, context, binding);
    }

    /**
     * 序列化器生成入口, 暴露给 Resolver 以便在解析嵌套或复杂泛型类型时调用.
     *
     * @param type    需要生成的 Java 类型
     * @param context 当前的序列化上下文
     * @param binding 构造器或字段绑定配置
     * @param <T>     目标 Java 类型
     * @return 对应类型的 NodeSerializer 实例
     */
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> createInternal(Type type, AutoSerializerContext context, @Nullable AutoSerializerBinding binding) {
        // 将类型规范化
        Type resolvedType = TypeUtils.normalize(type, Map.of());
        // 检查缓存中是否存在已经构建好的序列化器
        NodeSerializer<T> existing = context.getRegistry().get(resolvedType);
        if (existing != null) {
            return existing;
        }
        // 如果正在解析的和之前解析出现相同, 代表出现递归结构, 返回Lazy实现
        if (context.isResolving(resolvedType)) {
            return NodeSerializers.lazy(() -> context.getRegistry().get(resolvedType));
        }
        // 标记为正在解析, 然后获取
        context.pushResolving(resolvedType);
        NodeSerializer<T> serializer;
        Class<T> rawType = (Class<T>) TypeUtils.rawType(resolvedType); // 获取原始类型
        // 接口/抽象类/数组在未手动注册序列化器时无法自动实例化
        if (rawType.isInterface()) {
            throw new AutoSerializerException("No serializer registered for interface " + rawType.getName() + "; register a composed serializer before auto-serializing a class that uses it as a field type");
        }
        if (Modifier.isAbstract(rawType.getModifiers()) && !rawType.isEnum()) {
            throw new AutoSerializerException("No serializer registered for abstract class " + rawType.getName() + "; register a composed serializer before auto-serializing a class that uses it as a field type");
        }
        if (rawType.isArray()) {
            throw new AutoSerializerException("No serializer registered for array type " + rawType.getName() + "; register a composed serializer before auto-serializing a class that uses it as a field type");
        }
        Map<TypeVariable<?>, Type> variables = TypeUtils.typeVariables(rawType, resolvedType); // 类型变量到实际类型的映射
        // 根据原始类型进行不同的构造
        serializer = rawType.isRecord()
                ? this.createRecordSerializer(rawType, variables, context)
                : this.createClassSerializer(rawType, variables, context, binding);
        // 构造完成, 注册到序列化注册表里
        context.popResolving(resolvedType);
        context.getRegistry().registerUnsafe(resolvedType, serializer);
        return serializer;
    }

    /**
     * 为 Record 类创建序列化器.
     *
     * @param rawType   Record 的原始 Class 类型
     * @param variables 解析后的泛型变量映射
     * @param context   当前的序列化上下文
     * @param <T>       Record 类型
     * @return 适用于该 Record 类型的节点序列化器
     * @throws AutoSerializerException 如果未找到对应的全参构造器则抛出异常
     */
    private <T> NodeSerializer<T> createRecordSerializer(Class<T> rawType, Map<TypeVariable<?>, Type> variables, AutoSerializerContext context) {
        // 获取 Record 类的构造函数参数
        RecordComponent[] components = rawType.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
        }

        // 尝试获取构造函数
        Constructor<T> constructor;
        try {
            constructor = rawType.getDeclaredConstructor(parameterTypes);
        } catch (ReflectiveOperationException e) {
            throw new AutoSerializerException("Cannot find record constructor for " + rawType.getName(), e);
        }

        // 收集成员
        List<SerializableMember> members = new ArrayList<>();
        List<ConstructorParameter> parameters = new ArrayList<>();
        
        for (RecordComponent component : components) {
            // 如果被标记了忽略, 则创建被忽略的Member实例.
            if (component.isAnnotationPresent(YamlIgnore.class)) {
                members.add(RecordMember.ignored(component.getType()));
                parameters.add(new SimpleConstructorParameter(null, component.getType(), null));
                continue;
            }
            // 检查字段是否存在 YamlProperty 注解, 如果存在则采用注解声明的名称.
            YamlProperty property = component.getAnnotation(YamlProperty.class);
            String name = property != null ? property.value() : component.getName();
            // 获取规范化后的类型, 然后解析为一个 NodeSerializer.
            Type memberType = TypeUtils.normalize(component.getGenericType(), variables);
            NodeSerializer<?> serializer = context.resolve(memberType);
            
            members.add(RecordMember.active(name, memberType, serializer, component));
            parameters.add(new SimpleConstructorParameter(name, memberType, serializer));
        }

        InstantiationPlan<T> plan = new ConstructorPlan<>(constructor, parameters, members);
        return this.createPlanSerializer(rawType, plan);
    }

    /**
     * 为普通 Class 类创建序列化器.
     * 该方法会尝试从绑定配置、注解或可用的构造函数中挑选出最适合的实例化策略.
     *
     * @param rawType   普通类的原始 Class 类型
     * @param variables 解析后的泛型变量映射
     * @param context   当前的序列化上下文
     * @param binding   可选的绑定配置
     * @param <T>       Java 类型
     * @return 适用于该类的节点序列化器
     * @throws AutoSerializerException 如果找不到合适的构造函数则抛出异常
     */
    private <T> NodeSerializer<T> createClassSerializer(Class<T> rawType, Map<TypeVariable<?>, Type> variables, AutoSerializerContext context, AutoSerializerBinding binding) {
        List<SerializableMember> fields = collectFields(rawType, variables, context);
        
        AutoSerializerBinding.ConstructorBinding externalConstructor = binding != null ? binding.constructorBinding() : null;
        if (externalConstructor != null) {
            return createPlanSerializer(rawType, buildConstructorPlan(rawType, variables, context, externalConstructor, fields));
        }

        Constructor<T> annotatedConstructor = findAnnotatedConstructor(rawType);
        if (annotatedConstructor != null) {
            return createPlanSerializer(rawType, buildConstructorPlan(rawType, variables, context, annotatedConstructor, parameterNames(annotatedConstructor), fields));
        }

        Constructor<T> noArgsConstructor = findNoArgsConstructor(rawType);
        if (noArgsConstructor != null) {
            return createPlanSerializer(rawType, new FieldInjectionPlan<>(noArgsConstructor, fields));
        }

        Constructor<T>[] constructors = declaredConstructors(rawType);
        if (constructors.length == 1) {
            Constructor<T> constructor = constructors[0];
            List<String> names = parameterNames(constructor);
            if (!names.contains(null)) {
                return createPlanSerializer(rawType, buildConstructorPlan(rawType, variables, context, constructor, names, fields));
            }
        } else if (constructors.length > 1) {
            return createPlanSerializer(rawType, new UnsafeInstantiationPlan<>(rawType, fields));
        }

        throw new AutoSerializerException("Cannot select constructor for " + rawType.getName() + "; provide external constructor binding, @YamlConstructor, or a no-args constructor");
    }

    /**
     * 根据外部绑定配置, 构建一个有参构造函数的执行计划.
     *
     * @param rawType   目标原始类
     * @param variables 泛型映射
     * @param context   当前序列化上下文
     * @param binding   外部提供的构造函数绑定配置
     * @param fields    收集到的可序列化字段成员列表
     * @param <T>       目标 Java 类型
     * @return 构建好的 ConstructorPlan 执行计划
     * @throws AutoSerializerException 如果根据绑定的参数类型找不到对应的构造函数, 或参数数量不匹配
     */
    private <T> InstantiationPlan<T> buildConstructorPlan(Class<T> rawType, Map<TypeVariable<?>, Type> variables, AutoSerializerContext context, AutoSerializerBinding.ConstructorBinding binding, List<SerializableMember> fields) {
        Constructor<T> constructor;
        try {
            constructor = rawType.getDeclaredConstructor(binding.parameterTypes());
        } catch (NoSuchMethodException e) {
            throw new AutoSerializerException("Cannot find externally bound constructor for " + rawType.getName(), e);
        }
        List<String> names = binding.parameterNames();
        if (names.size() != constructor.getParameterCount()) {
            throw new AutoSerializerException("Constructor binding parameter count does not match constructor for " + rawType.getName());
        }
        return buildConstructorPlan(rawType, variables, context, constructor, names, fields);
    }

    /**
     * 根据已知的有参构造函数及其参数名称列表, 构建执行计划.
     * 会同时保留供反序列化阶段使用的构造参数列表, 以及供序列化编码使用的所有字段列表.
     *
     * @param rawType     目标原始类
     * @param variables   泛型映射
     * @param context     当前序列化上下文
     * @param constructor 选定的构造函数
     * @param names       构造函数参数的 YAML 键名列表
     * @param fields      收集到的所有可序列化字段列表
     * @param <T>         目标 Java 类型
     * @return 构建好的 ConstructorPlan 执行计划
     * @throws AutoSerializerException 如果某个构造器参数没有对应的 YAML 键名
     */
    private <T> InstantiationPlan<T> buildConstructorPlan(
            Class<T> rawType,
            Map<TypeVariable<?>, Type> variables,
            AutoSerializerContext context,
            Constructor<T> constructor,
            List<String> names,
            List<SerializableMember> fields
    ) {
        Type[] parameterTypes = constructor.getGenericParameterTypes();
        List<SerializableMember> combinedMembers = new ArrayList<>(fields);
        List<ConstructorParameter> parameters = new ArrayList<>();
        
        for (int i = 0; i < parameterTypes.length; i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) {
                throw new AutoSerializerException("Constructor parameter " + i + " of " + rawType.getName() + " has no YAML key");
            }
            Type resolvedType = TypeUtils.normalize(parameterTypes[i], variables);
            NodeSerializer<?> serializer = context.resolve(resolvedType);
            
            // 将构造参数加入到用于反序列化调用的参数列表中
            parameters.add(new SimpleConstructorParameter(name, resolvedType, serializer));
        }

        return new ConstructorPlan<>(constructor, parameters, combinedMembers);
    }

    /**
     * 基于预先生成好的 InstantiationPlan 实例化策略计划, 构建并返回一个 NodeSerializer.
     * 实现反序列化时的对象构建与字段注入, 以及序列化时的字段遍历与编码.
     *
     * @param rawType 目标 Java 类型(仅供泛型签名使用)
     * @param plan    实例化与执行计划
     * @param <T>     目标 Java 类型
     * @return 能够处理指定类型的节点序列化器
     */
    private <T> NodeSerializer<T> createPlanSerializer(Class<T> rawType, InstantiationPlan<T> plan) {
        List<SerializableMember> injectables = new ArrayList<>();
        List<SerializableMember> encodables = new ArrayList<>();
        for (SerializableMember member : plan.members()) {
            if (!member.isIgnored()) {
                encodables.add(member);
            }
            if (plan.shouldInject(member)) {
                injectables.add(member);
            }
        }
        
        SerializableMember[] injectableArr = injectables.toArray(new SerializableMember[0]);
        SerializableMember[] encodableArr = encodables.toArray(new SerializableMember[0]);
        int mapCapacity = Math.max((int) (encodableArr.length / 0.75f) + 1, 16);

        return NodeSerializer.createInternal(
            rawType,
            node -> {
                if (!(node instanceof SectionNode sectionNode)) {
                    return null;
                }

                T instance = plan.instantiate(sectionNode);

                // 字段注入过程
                for (SerializableMember member : injectableArr) {
                    YamlNode<?> child = sectionNode.getNodeOrNull(member.name());
                    if (child == null) {
                        continue;
                    }
                    Object decoded = member.serializer().deserialize(child);
                    member.set(instance, decoded != null ? decoded : TypeUtils.defaultValue(TypeUtils.rawType(member.type())));
                }

                return instance;
            },
            value -> {
                if (value == null) {
                    return null;
                }
                Map<String, Object> map = new LinkedHashMap<>(mapCapacity);
                for (SerializableMember member : encodableArr) {
                    map.put(member.name(), member.encode(value));
                }
                return map;
            }
        );
    }
    
    /**
     * 收集类及其父类中可参与序列化的字段, 并将其封装为 SerializableMember 列表.
     * 父类字段会排在子类字段之前. static、transient 以及被 @YamlIgnore 标记的字段将被忽略.
     *
     * @param rawType   需要收集字段的原始类
     * @param variables 当前泛型映射上下文
     * @param context   当前的序列化上下文
     * @return 收集并封装好的 SerializableMember(主要为 FieldMember)列表
     */
    private List<SerializableMember> collectFields(Class<?> rawType, Map<TypeVariable<?>, Type> variables, AutoSerializerContext context) {
        List<SerializableMember> result = new ArrayList<>();
        Map<String, Field> fieldNames = new LinkedHashMap<>();
        for (ClassFieldContext fieldContext : classHierarchy(rawType, variables)) {
            for (Field field : fieldContext.type().getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                YamlProperty property = field.getAnnotation(YamlProperty.class);
                String name = property != null ? property.value() : field.getName();
                Field previous = fieldNames.putIfAbsent(name, field);
                if (previous != null) {
                    throw duplicateYamlField(rawType, name, previous, field);
                }
                Type fieldType = TypeUtils.normalize(field.getGenericType(), fieldContext.variables());
                NodeSerializer<?> serializer = context.resolve(fieldType);
                boolean isFinal = Modifier.isFinal(modifiers);
                result.add(new FieldMember(name, fieldType, serializer, field, true, isFinal));
            }
        }
        return result;
    }

    /**
     * 构造父类优先的类层级, 同时解析每一层声明字段时需要使用的泛型变量.
     */
    private List<ClassFieldContext> classHierarchy(Class<?> rawType, Map<TypeVariable<?>, Type> variables) {
        LinkedList<ClassFieldContext> result = new LinkedList<>();
        Class<?> currentType = rawType;
        Map<TypeVariable<?>, Type> currentVariables = variables;
        while (currentType != null && currentType != Object.class) {
            result.addFirst(new ClassFieldContext(currentType, Map.copyOf(currentVariables)));
            Type genericSuperType = currentType.getGenericSuperclass();
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

    private Map<TypeVariable<?>, Type> resolveSuperVariables(
            Class<?> superType,
            ParameterizedType parameterizedSuperType,
            Map<TypeVariable<?>, Type> childVariables
    ) {
        TypeVariable<?>[] variables = superType.getTypeParameters();
        Type[] arguments = parameterizedSuperType.getActualTypeArguments();
        Map<TypeVariable<?>, Type> result = new HashMap<>(Math.max((int) (variables.length / 0.75f) + 1, 16));
        for (int i = 0; i < variables.length; i++) {
            result.put(variables[i], TypeUtils.normalize(arguments[i], childVariables));
        }
        return result;
    }

    private AutoSerializerException duplicateYamlField(Class<?> rawType, String name, Field previous, Field current) {
        return new AutoSerializerException("Duplicate YAML field '" + name + "' while auto-serializing "
                + rawType.getName() + ": "
                + previous.getDeclaringClass().getName() + "." + previous.getName()
                + " conflicts with "
                + current.getDeclaringClass().getName() + "." + current.getName());
    }

    private record ClassFieldContext(Class<?> type, Map<TypeVariable<?>, Type> variables) {
    }

    /**
     * 查找类中被 @YamlConstructor 注解标记的唯一构造函数.
     *
     * @param rawType 目标类
     * @param <T>     Java 类型
     * @return 找到的被注解标记的构造函数, 若不存在则返回 null
     * @throws AutoSerializerException 如果存在多个构造函数带有该注解
     */
    @SuppressWarnings("unchecked")
    private <T> Constructor<T> findAnnotatedConstructor(Class<T> rawType) {
        Constructor<T> result = null;
        for (Constructor<?> constructor : rawType.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(YamlConstructor.class)) {
                if (result != null) {
                    throw new AutoSerializerException("Multiple @YamlConstructor constructors found for " + rawType.getName());
                }
                result = (Constructor<T>) constructor;
            }
        }
        return result;
    }

    /**
     * 查找类中的无参构造函数.
     *
     * @param rawType 目标类
     * @param <T>     Java 类型
     * @return 无参构造函数, 若未声明无参构造则返回 null
     */
    private <T> Constructor<T> findNoArgsConstructor(Class<T> rawType) {
        try {
            return rawType.getDeclaredConstructor();
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /**
     * 获取类的所有已声明构造函数数组.
     *
     * @param rawType 目标类
     * @param <T>     Java 类型
     * @return 构造函数数组
     */
    @SuppressWarnings("unchecked")
    private <T> Constructor<T>[] declaredConstructors(Class<T> rawType) {
        return (Constructor<T>[]) rawType.getDeclaredConstructors();
    }

    /**
     * 解析并获取构造函数所有参数在 YAML 中对应的键名列表.
     * 优先读取 @YamlProperty 注解; 如果存在并且开启了保留参数名编译选项, 则回退到真实的参数名; 否则为 null.
     *
     * @param constructor 需要解析参数的构造函数
     * @return 与构造函数参数顺序对应的键名列表
     */
    private List<String> parameterNames(Constructor<?> constructor) {
        List<String> names = new ArrayList<>();
        for (Parameter parameter : constructor.getParameters()) {
            YamlProperty property = parameter.getAnnotation(YamlProperty.class);
            if (property != null) {
                names.add(property.value());
            } else if (parameter.isNamePresent()) {
                names.add(parameter.getName());
            } else {
                names.add(null);
            }
        }
        return names;
    }

    /**
     * 内部记录类, 用于在处理构造器参数时简单封装所需的键名、类型和对应的序列化器.
     */
    private record SimpleConstructorParameter(String name, Type type, NodeSerializer<?> serializer) implements ConstructorParameter {}
}
