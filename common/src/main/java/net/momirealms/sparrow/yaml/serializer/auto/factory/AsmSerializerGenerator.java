package net.momirealms.sparrow.yaml.serializer.auto.factory;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V17;

/**
 * 负责生成运行期 NodeSerializer 实现类的 ASM 字节码.
 */
final class AsmSerializerGenerator {
    private static final AtomicLong ID = new AtomicLong(); // 生成类名的递增后缀.
    private static final String SER = "net/momirealms/sparrow/yaml/serializer/NodeSerializer"; // NodeSerializer 内部名.
    private static final String FA = "net/momirealms/sparrow/yaml/serializer/auto/accessor/FieldAccessor"; // FieldAccessor 内部名.
    private static final String YN = "net/momirealms/sparrow/yaml/node/YamlNode"; // YamlNode 内部名.
    private static final String SEC = "net/momirealms/sparrow/yaml/node/SectionNode"; // SectionNode 内部名.

    private AsmSerializerGenerator() {
    }

    /**
     * 生成目标类型对应的 NodeSerializer 实现类字节码.
     */
    static byte[] generate(
            ClassMeta meta,
            List<ClassMeta.FieldBinding> all,
            List<ClassMeta.FieldBinding> fields,
            ConstructorChoice constructor,
            String targetDesc
    ) {
        String generatedName = meta.internalName + "$$Serializer_" + ID.incrementAndGet();
        String targetName = meta.internalName;
        String serializersDesc = "[L" + SER + ";";
        String accessorsDesc = "[L" + FA + ";";
        String signature = "L" + SER + "<" + targetDesc + ">;";

        // 创建类结构和运行期依赖数组字段.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC | ACC_FINAL, generatedName, signature, "java/lang/Object", new String[]{SER});
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "sers", serializersDesc, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "accessors", accessorsDesc, null, null).visitEnd();

        // 生成构造器、decode、encode 三个核心方法.
        genInit(writer, generatedName, serializersDesc, accessorsDesc);
        genDec(writer, generatedName, targetName, all, fields, constructor);
        genEnc(writer, generatedName, targetName, fields);

        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * 生成保存 NodeSerializer[] 和 FieldAccessor[] 的构造器.
     */
    private static void genInit(
            ClassWriter writer,
            String generatedName,
            String serializersDesc,
            String accessorsDesc
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "([L" + SER + ";[L" + FA + ";)V", null, null);
        method.visitCode();

        // super();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        // this.sers = sers;
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitFieldInsn(PUTFIELD, generatedName, "sers", serializersDesc);

        // this.accessors = accessors;
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 2);
        method.visitFieldInsn(PUTFIELD, generatedName, "accessors", accessorsDesc);

        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 生成 deserialize(YamlNode) 方法.
     */
    private static void genDec(
            ClassWriter writer,
            String generatedName,
            String targetName,
            List<ClassMeta.FieldBinding> all,
            List<ClassMeta.FieldBinding> fields,
            ConstructorChoice constructor
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "deserialize", "(L" + YN + ";)Ljava/lang/Object;", null, null);
        method.visitCode();

        // 入参必须是 SectionNode, 否则返回 null.
        Label sectionOk = new Label();
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(INSTANCEOF, SEC);
        method.visitJumpInsn(IFNE, sectionOk);
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);

        method.visitLabel(sectionOk);
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(CHECKCAST, SEC);
        int sectionVar = 2;
        method.visitVarInsn(ASTORE, sectionVar);
        int instanceVar = sectionVar + 1;

        // 根据构造器策略创建实例.
        if (constructor.hasArgs) {
            method.visitTypeInsn(NEW, targetName);
            method.visitInsn(DUP);

            String constructorDesc = ctorDesc(constructor, all);
            emitConstructorArguments(method, generatedName, all, constructor, sectionVar, instanceVar + 2);
            method.visitMethodInsn(INVOKESPECIAL, targetName, "<init>", constructorDesc, false);
            method.visitVarInsn(ASTORE, instanceVar);

            injFields(method, generatedName, fields, sectionVar, instanceVar, instanceVar + 2 + constructor.paramKeys.size(), constructor);
        } else {
            method.visitTypeInsn(NEW, targetName);
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, targetName, "<init>", "()V", false);
            method.visitVarInsn(ASTORE, instanceVar);

            injFields(method, generatedName, fields, sectionVar, instanceVar, instanceVar + 2, constructor);
        }

        // 返回构造完成并注入字段后的实例.
        method.visitVarInsn(ALOAD, instanceVar);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 生成构造器参数描述符.
     */
    private static String ctorDesc(ConstructorChoice constructor, List<ClassMeta.FieldBinding> all) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < constructor.paramKeys.size(); i++) {
            int serializerIndex = constructor.paramSerIndices.get(i);
            builder.append(all.get(serializerIndex).field.descriptor);
        }
        builder.append(")V");
        return builder.toString();
    }

    /**
     * 将构造器参数依次压入操作数栈.
     */
    private static void emitConstructorArguments(
            MethodVisitor method,
            String generatedName,
            List<ClassMeta.FieldBinding> all,
            ConstructorChoice constructor,
            int sectionVar,
            int tempStartVar
    ) {
        for (int i = 0; i < constructor.paramKeys.size(); i++) {
            String key = constructor.paramKeys.get(i);
            int serializerIndex = constructor.paramSerIndices.get(i);
            String descriptor = all.get(serializerIndex).field.descriptor;

            // 被忽略的 record 组件没有 YAML 键名, 直接使用类型默认值.
            if (key == null) {
                pushDef(method, descriptor);
                continue;
            }

            emitGetChildNode(method, sectionVar, key);

            int tempNodeVar = tempStartVar + i;
            method.visitVarInsn(ASTORE, tempNodeVar);

            method.visitVarInsn(ALOAD, 0);
            method.visitFieldInsn(GETFIELD, generatedName, "sers", "[L" + SER + ";");
            method.visitLdcInsn(serializerIndex);
            method.visitInsn(AALOAD);
            method.visitVarInsn(ALOAD, tempNodeVar);
            method.visitMethodInsn(INVOKEINTERFACE, SER, "deserialize", "(L" + YN + ";)Ljava/lang/Object;", true);

            // 构造器基本类型参数缺失时使用 Java 默认值, 避免 null 拆箱.
            if (isPrimitive(descriptor)) {
                emitPrimitiveDecodeFallback(method, descriptor, tempStartVar + constructor.paramKeys.size() + i);
            } else {
                castUnbox(method, descriptor);
            }
        }
    }

    /**
     * 生成构造完成后的字段注入代码.
     */
    private static void injFields(
            MethodVisitor method,
            String generatedName,
            List<ClassMeta.FieldBinding> fields,
            int sectionVar,
            int instanceVar,
            int nodeVar,
            ConstructorChoice constructor
    ) {
        for (int i = 0; i < fields.size(); i++) {
            ClassMeta.FieldBinding binding = fields.get(i);
            if (binding.field == null || binding.field.hasYamlIgnore) {
                continue;
            }
            if (isConstructorParameter(binding.yamlKey(), constructor)) {
                continue;
            }

            Label skip = new Label();

            // 从 SectionNode 中读取当前字段对应的 YamlNode.
            emitGetChildNode(method, sectionVar, binding.yamlKey());
            method.visitVarInsn(ASTORE, nodeVar);
            method.visitVarInsn(ALOAD, nodeVar);
            method.visitJumpInsn(IFNULL, skip);

            // 使用字段绑定的 serializer 解码节点.
            method.visitVarInsn(ALOAD, 0);
            method.visitFieldInsn(GETFIELD, generatedName, "sers", "[L" + SER + ";");
            method.visitLdcInsn(binding.index);
            method.visitInsn(AALOAD);
            method.visitVarInsn(ALOAD, nodeVar);
            method.visitMethodInsn(INVOKEINTERFACE, SER, "deserialize", "(L" + YN + ";)Ljava/lang/Object;", true);

            // 处理基本类型默认值、拆箱或对象类型强转.
            String descriptor = binding.field.descriptor;
            if (isPrimitive(descriptor)) {
                emitPrimitiveDecodeFallback(method, descriptor, nodeVar + 1);
                box(method, descriptor);
            } else if (descriptor.startsWith("L")) {
                method.visitTypeInsn(CHECKCAST, descriptor.substring(1, descriptor.length() - 1));
            } else if (descriptor.startsWith("[")) {
                method.visitTypeInsn(CHECKCAST, descriptor);
            }

            // 通过 FieldAccessor 写入目标字段, 兼容父类字段和 private 字段.
            int valueVar = nodeVar + 2;
            method.visitVarInsn(ASTORE, valueVar);
            emitLoadAccessor(method, generatedName, binding.accessorIndex);
            method.visitVarInsn(ALOAD, instanceVar);
            method.visitVarInsn(ALOAD, valueVar);
            method.visitMethodInsn(INVOKEINTERFACE, FA, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
            method.visitLabel(skip);
        }
    }

    /**
     * 生成通过单个 YAML 键读取子节点的指令.
     */
    private static void emitGetChildNode(MethodVisitor method, int sectionVar, String key) {
        method.visitVarInsn(ALOAD, sectionVar);
        method.visitInsn(ICONST_1);
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        method.visitInsn(DUP);
        method.visitInsn(ICONST_0);
        method.visitLdcInsn(key);
        method.visitInsn(AASTORE);
        method.visitMethodInsn(INVOKEVIRTUAL, SEC, "getNodeOrNull", "([Ljava/lang/Object;)L" + YN + ";", false);
    }

    /**
     * 判断字段是否已经通过构造器参数完成赋值.
     */
    private static boolean isConstructorParameter(String yamlKey, ConstructorChoice constructor) {
        if (constructor.paramKeys == null) {
            return false;
        }

        for (int i = 0; i < constructor.paramKeys.size(); i++) {
            if (yamlKey.equals(constructor.paramKeys.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成基本类型字段的 null 回退逻辑.
     */
    private static void emitPrimitiveDecodeFallback(
            MethodVisitor method,
            String descriptor,
            int tempVar
    ) {
        method.visitVarInsn(ASTORE, tempVar);

        Label nonNull = new Label();
        Label done = new Label();
        method.visitVarInsn(ALOAD, tempVar);
        method.visitJumpInsn(IFNONNULL, nonNull);
        pushDef(method, descriptor);
        method.visitJumpInsn(GOTO, done);

        method.visitLabel(nonNull);
        method.visitVarInsn(ALOAD, tempVar);
        method.visitTypeInsn(CHECKCAST, boxName(descriptor));
        unbox(method, descriptor);

        method.visitLabel(done);
    }

    /**
     * 生成 serialize(Object) 方法.
     */
    private static void genEnc(
            ClassWriter writer,
            String generatedName,
            String targetName,
            List<ClassMeta.FieldBinding> fields
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "serialize", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        method.visitCode();

        // null 对象直接编码为 null.
        Label nonNull = new Label();
        method.visitVarInsn(ALOAD, 1);
        method.visitJumpInsn(IFNONNULL, nonNull);
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);

        method.visitLabel(nonNull);
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(CHECKCAST, targetName);
        int valueVar = 2;
        method.visitVarInsn(ASTORE, valueVar);

        // 创建结果 Map.
        int fieldCount = countEncodableFields(fields);
        int mapCapacity = Math.max((int) (fieldCount / 0.75f) + 1, 16);
        method.visitTypeInsn(NEW, "java/util/LinkedHashMap");
        method.visitInsn(DUP);
        method.visitLdcInsn(mapCapacity);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "(I)V", false);

        int mapVar = valueVar + 1;
        method.visitVarInsn(ASTORE, mapVar);

        // 逐字段编码并写入 Map.
        for (int i = 0; i < fields.size(); i++) {
            ClassMeta.FieldBinding binding = fields.get(i);
            if (binding.field == null || binding.field.hasYamlIgnore) {
                continue;
            }
            emitEncodeField(method, generatedName, binding, valueVar, mapVar);
        }

        method.visitVarInsn(ALOAD, mapVar);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 统计需要写入结果 Map 的字段数量.
     */
    private static int countEncodableFields(List<ClassMeta.FieldBinding> fields) {
        int count = 0;
        for (int i = 0; i < fields.size(); i++) {
            ClassMeta.FieldBinding binding = fields.get(i);
            if (binding.field != null && !binding.field.hasYamlIgnore) {
                count++;
            }
        }
        return count;
    }

    /**
     * 生成单个字段的 encode 和 Map.put 指令.
     */
    private static void emitEncodeField(
            MethodVisitor method,
            String generatedName,
            ClassMeta.FieldBinding binding,
            int valueVar,
            int mapVar
    ) {
        method.visitVarInsn(ALOAD, mapVar);
        method.visitLdcInsn(binding.yamlKey());

        String descriptor = binding.field.descriptor;
        emitLoadAccessor(method, generatedName, binding.accessorIndex);
        method.visitVarInsn(ALOAD, valueVar);
        method.visitMethodInsn(INVOKEINTERFACE, FA, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);

        // FieldAccessor 已返回 Object, 简单值直接放入 Map, 复杂对象交给字段 serializer.
        if (!isPrimitive(descriptor) && !descriptor.equals("Ljava/lang/String;") && !isBoxed(descriptor)) {
            int serializedValueVar = mapVar + 1;
            method.visitVarInsn(ASTORE, serializedValueVar);

            method.visitVarInsn(ALOAD, 0);
            method.visitFieldInsn(GETFIELD, generatedName, "sers", "[L" + SER + ";");
            method.visitLdcInsn(binding.index);
            method.visitInsn(AALOAD);
            method.visitVarInsn(ALOAD, serializedValueVar);
            method.visitMethodInsn(INVOKEINTERFACE, SER, "serialize", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        }

        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        method.visitInsn(POP);
    }

    /**
     * 将指定字段 accessor 压入操作数栈.
     */
    private static void emitLoadAccessor(MethodVisitor method, String generatedName, int accessorIndex) {
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, generatedName, "accessors", "[L" + FA + ";");
        method.visitLdcInsn(accessorIndex);
        method.visitInsn(AALOAD);
    }

    /**
     * 对 decode 结果进行目标类型转换或拆箱.
     */
    static void castUnbox(MethodVisitor method, String descriptor) {
        if (isPrimitive(descriptor)) {
            method.visitTypeInsn(CHECKCAST, boxName(descriptor));
            unbox(method, descriptor);
        } else if (descriptor.startsWith("L")) {
            method.visitTypeInsn(CHECKCAST, descriptor.substring(1, descriptor.length() - 1));
        }
    }

    /**
     * 将指定描述符对应的 Java 默认值压入操作数栈.
     */
    static void pushDef(MethodVisitor method, String descriptor) {
        switch (descriptor) {
            case "I":
            case "S":
            case "B":
            case "Z":
            case "C":
                method.visitInsn(ICONST_0);
                break;
            case "J":
                method.visitInsn(LCONST_0);
                break;
            case "F":
                method.visitInsn(FCONST_0);
                break;
            case "D":
                method.visitInsn(DCONST_0);
                break;
            default:
                method.visitInsn(ACONST_NULL);
                break;
        }
    }

    /**
     * 将基本类型装箱为对应包装类型.
     */
    static void box(MethodVisitor method, String descriptor) {
        switch (descriptor) {
            case "I":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                break;
            case "J":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case "D":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case "F":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                break;
            case "Z":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                break;
            case "B":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                break;
            case "S":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                break;
            case "C":
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                break;
            default:
                break;
        }
    }

    /**
     * 将包装类型拆箱为对应基本类型.
     */
    static void unbox(MethodVisitor method, String descriptor) {
        switch (descriptor) {
            case "I":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                break;
            case "J":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                break;
            case "D":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                break;
            case "F":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                break;
            case "Z":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                break;
            case "B":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                break;
            case "S":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                break;
            case "C":
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                break;
            default:
                break;
        }
    }

    /**
     * 判断 JVM 描述符是否表示基本类型.
     */
    static boolean isPrimitive(String descriptor) {
        return descriptor.length() == 1;
    }

    /**
     * 判断 JVM 描述符是否表示 Java 基础包装类型.
     */
    static boolean isBoxed(String descriptor) {
        return descriptor.startsWith("Ljava/lang/")
                && (descriptor.endsWith("Integer;")
                || descriptor.endsWith("Long;")
                || descriptor.endsWith("Double;")
                || descriptor.endsWith("Float;")
                || descriptor.endsWith("Boolean;")
                || descriptor.endsWith("Byte;")
                || descriptor.endsWith("Short;")
                || descriptor.endsWith("Character;"));
    }

    /**
     * 获取基本类型描述符对应的包装类型内部名.
     */
    static String boxName(String descriptor) {
        return switch (descriptor) {
            case "I" -> "java/lang/Integer";
            case "J" -> "java/lang/Long";
            case "D" -> "java/lang/Double";
            case "F" -> "java/lang/Float";
            case "Z" -> "java/lang/Boolean";
            case "B" -> "java/lang/Byte";
            case "S" -> "java/lang/Short";
            case "C" -> "java/lang/Character";
            default -> throw new IllegalArgumentException(descriptor);
        };
    }

    /**
     * 描述生成 decode 方法时采用的构造器调用方式.
     */
    static final class ConstructorChoice {
        static final ConstructorChoice NO_ARGS = new ConstructorChoice(false, List.of(), List.of()); // 无参构造策略.

        final boolean hasArgs; // 是否需要调用带参构造器.
        final List<String> paramKeys; // 构造器参数对应的 YAML 键名.
        final List<Integer> paramSerIndices; // 构造器参数对应的 serializer 数组索引.

        ConstructorChoice(
                boolean hasArgs,
                List<String> paramKeys,
                List<Integer> paramSerIndices
        ) {
            this.hasArgs = hasArgs;
            this.paramKeys = paramKeys;
            this.paramSerIndices = paramSerIndices;
        }
    }
}
