package net.momirealms.sparrow.yaml.serializer.auto.accessor;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V17;

/**
 * 为非 final 字段生成基于 ASM 的 {@link FieldAccessor} 实现.
 *
 * <p>生成类会以 hidden nestmate 形式定义到字段声明类的 lookup 中,
 * 因此可以直接访问 private 字段. final 字段由 {@link FieldAccessors}
 * 在进入本生成器前转交给反射 fallback.</p>
 */
final class AsmFieldAccessorGenerator {
    private static final AtomicLong ID = new AtomicLong(); // 避免同一目标类中生成类名冲突
    private static final String ACCESSOR = Type.getInternalName(FieldAccessor.class); // FieldAccessor 内部名

    private AsmFieldAccessorGenerator() {
    }

    /**
     * 生成并实例化指定字段的 ASM 访问器.
     *
     * @param field 目标字段
     * @return 可直接读写目标字段的访问器
     */
    static FieldAccessor generate(Field field) {
        try {
            byte[] bytes = generateBytes(field);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    field.getDeclaringClass(),
                    MethodHandles.lookup()
            );
            MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                    bytes,
                    true,
                    MethodHandles.Lookup.ClassOption.NESTMATE
            );
            return (FieldAccessor) hiddenLookup.findConstructor(
                    hiddenLookup.lookupClass(),
                    MethodType.methodType(void.class)
            ).invoke();
        } catch (Throwable e) {
            throw new AutoSerializerException("Cannot generate field accessor for " + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }

    /**
     * 构建访问器类字节码.
     */
    private static byte[] generateBytes(Field field) {
        Class<?> owner = field.getDeclaringClass();
        String ownerName = Type.getInternalName(owner);
        String generatedName = ownerName + "$$FieldAccessor_" + ID.incrementAndGet();
        String descriptor = Type.getDescriptor(field.getType());
        boolean canSet = !Modifier.isFinal(field.getModifiers());

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC | ACC_FINAL, generatedName, null, "java/lang/Object", new String[]{ACCESSOR});

        // 访问器包含无参构造, get, set 和 canSet 四个方法.
        genInit(writer);
        genGet(writer, ownerName, field.getName(), descriptor);
        genSet(writer, ownerName, field.getName(), descriptor, canSet, field);
        genCanSet(writer, canSet);

        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * 生成访问器无参构造.
     */
    private static void genInit(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 生成字段读取方法, 基本类型返回前会被装箱.
     */
    private static void genGet(
            ClassWriter writer,
            String ownerName,
            String fieldName,
            String descriptor
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(CHECKCAST, ownerName);
        method.visitFieldInsn(GETFIELD, ownerName, fieldName, descriptor);
        box(method, descriptor);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 生成字段写入方法.
     *
     * <p>非 final 字段直接 {@code PUTFIELD}. 这里仍保留 final 字段防御分支,
     * 以便未来有人绕过 {@link FieldAccessors} 直接调用本生成器时快速失败.</p>
     */
    private static void genSet(
            ClassWriter writer,
            String ownerName,
            String fieldName,
            String descriptor,
            boolean canSet,
            Field field
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
        method.visitCode();

        if (!canSet) {
            method.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
            method.visitInsn(DUP);
            method.visitLdcInsn("Cannot set final field " + field.getDeclaringClass().getName() + "." + field.getName() + " without constructor binding");
            method.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
            method.visitInsn(ATHROW);
        } else {
            method.visitVarInsn(ALOAD, 1);
            method.visitTypeInsn(CHECKCAST, ownerName);
            method.visitVarInsn(ALOAD, 2);
            castUnbox(method, descriptor);
            method.visitFieldInsn(PUTFIELD, ownerName, fieldName, descriptor);
            method.visitInsn(RETURN);
        }

        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 生成写入能力标记方法.
     */
    private static void genCanSet(ClassWriter writer, boolean canSet) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "canSet", "()Z", null, null);
        method.visitCode();
        method.visitInsn(canSet ? ICONST_1 : ICONST_0);
        method.visitInsn(IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * 将 Object 类型入参转换为字段描述符对应的 JVM 值.
     */
    private static void castUnbox(MethodVisitor method, String descriptor) {
        switch (descriptor) {
            case "I":
                method.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                break;
            case "J":
                method.visitTypeInsn(CHECKCAST, "java/lang/Long");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                break;
            case "D":
                method.visitTypeInsn(CHECKCAST, "java/lang/Double");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                break;
            case "F":
                method.visitTypeInsn(CHECKCAST, "java/lang/Float");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                break;
            case "Z":
                method.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                break;
            case "B":
                method.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                break;
            case "S":
                method.visitTypeInsn(CHECKCAST, "java/lang/Short");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                break;
            case "C":
                method.visitTypeInsn(CHECKCAST, "java/lang/Character");
                method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                break;
            default:
                method.visitTypeInsn(CHECKCAST, castName(descriptor));
                break;
        }
    }

    /**
     * 将字段读取结果装箱为 Object 返回值.
     */
    private static void box(MethodVisitor method, String descriptor) {
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
     * 从对象字段描述符提取可用于 CHECKCAST 的内部类名.
     */
    private static String castName(String descriptor) {
        if (descriptor.startsWith("L")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        return descriptor;
    }
}
