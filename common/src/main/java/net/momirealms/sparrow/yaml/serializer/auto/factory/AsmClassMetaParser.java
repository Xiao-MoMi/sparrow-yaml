package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_RECORD;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.objectweb.asm.Opcodes.ASM9;

/**
 * 使用 ASM ClassReader 读取类结构元数据, 避免在解析阶段依赖大量反射遍历.
 */
final class AsmClassMetaParser {
    private static final String YAML_PROPERTY_DESC = Type.getDescriptor(YamlProperty.class); // @YamlProperty 描述符.
    private static final String YAML_IGNORE_DESC = Type.getDescriptor(YamlIgnore.class); // @YamlIgnore 描述符.
    private static final String YAML_CONSTRUCTOR_DESC = Type.getDescriptor(YamlConstructor.class); // @YamlConstructor 描述符.

    private AsmClassMetaParser() {
    }

    /**
     * 将目标类的字节码解析为 ClassMeta.
     */
    static ClassMeta parse(
            Class<?> rawType,
            byte[] classBytes,
            AutoSerializerBinding binding
    ) {
        ClassReader reader = new ClassReader(classBytes);
        ClassMeta meta = new ClassMeta();
        List<ClassMeta.ConstructorEntry> constructors = new ArrayList<>();

        // 读取类、字段、构造器的基础元数据.
        reader.accept(new ClassVisitor(ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                meta.internalName = name;
                meta.className = name.replace('/', '.');
                meta.superName = superName;
                meta.isRecord = (access & ACC_RECORD) != 0;
                meta.isInterface = (access & ACC_INTERFACE) != 0;
                meta.isAbstract = (access & ACC_ABSTRACT) != 0;
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!"<init>".equals(name)) {
                    return null;
                }

                ClassMeta.ConstructorEntry entry = new ClassMeta.ConstructorEntry();
                entry.descriptor = descriptor;

                // 拆出构造器参数描述符, 供调试和后续扩展使用.
                Type methodType = Type.getMethodType(descriptor);
                Type[] argumentTypes = methodType.getArgumentTypes();
                entry.paramDescriptors = new ArrayList<>(argumentTypes.length);
                for (int i = 0; i < argumentTypes.length; i++) {
                    Type argumentType = argumentTypes[i];
                    entry.paramDescriptors.add(argumentType.getDescriptor());
                }
                constructors.add(entry);

                return new MethodVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                        if (YAML_CONSTRUCTOR_DESC.equals(annDesc)) {
                            entry.hasYamlConstructor = true;
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

        // 将 visitor 收集到的数据写回 ClassMeta.
        meta.fields = parseFields(rawType, classBytes, Map.of());
        meta.constructors = constructors;
        return meta;
    }

    /**
     * 解析指定类自身声明的可序列化字段. 调用方负责按继承层级组合结果.
     */
    static List<ClassMeta.FieldEntry> parseFields(
            Class<?> ownerType,
            byte[] classBytes,
            Map<TypeVariable<?>, java.lang.reflect.Type> typeVariables
    ) {
        ClassReader reader = new ClassReader(classBytes);
        List<ClassMeta.FieldEntry> fields = new ArrayList<>();

        reader.accept(new ClassVisitor(ASM9) {
            private String ownerInternalName;

            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                this.ownerInternalName = name;
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if ((access & ACC_STATIC) != 0 || (access & ACC_TRANSIENT) != 0) {
                    return null;
                }

                ClassMeta.FieldEntry entry = new ClassMeta.FieldEntry();
                entry.name = name;
                entry.descriptor = descriptor;
                entry.signature = signature;
                entry.access = access;
                entry.ownerClass = ownerType;
                entry.ownerInternalName = ownerInternalName;
                entry.reflectiveField = resolveField(ownerType, name);
                entry.genericType = entry.reflectiveField.getGenericType();
                entry.typeVariables = Map.copyOf(typeVariables);
                fields.add(entry);

                return new FieldVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                        if (YAML_PROPERTY_DESC.equals(annDesc)) {
                            entry.hasYamlProperty = true;
                            return new AnnotationVisitor(ASM9) {
                                @Override
                                public void visit(String annName, Object annValue) {
                                    if ("value".equals(annName)) {
                                        entry.yamlPropertyValue = (String) annValue;
                                    }
                                }
                            };
                        }

                        if (YAML_IGNORE_DESC.equals(annDesc)) {
                            entry.hasYamlIgnore = true;
                        }

                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

        return fields;
    }

    /**
     * 通过反射补齐字段信息.
     */
    private static Field resolveField(Class<?> rawType, String name) {
        try {
            return rawType.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AutoSerializerException("Cannot resolve field " + rawType.getName() + "." + name, e);
        }
    }
}
