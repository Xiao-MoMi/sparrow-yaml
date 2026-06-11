package net.momirealms.sparrow.yaml.serializer.auto.factory;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
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

final class AsmSerializerGenerator {
    private static final AtomicLong ID = new AtomicLong();
    private static final String GENERATED_PREFIX = "net/momirealms/sparrow/yaml/serializer/auto/factory/BridgeSerializer_";
    private static final String SER = "net/momirealms/sparrow/yaml/serializer/NodeSerializer";
    private static final String DEC = "net/momirealms/sparrow/yaml/serializer/NodeSerializer$Decoder";
    private static final String ENC = "net/momirealms/sparrow/yaml/serializer/NodeSerializer$Encoder";
    private static final String FA = "net/momirealms/sparrow/yaml/serializer/auto/accessor/FieldAccessor";
    private static final String INST = "net/momirealms/sparrow/yaml/serializer/auto/factory/ObjectInstantiator";
    private static final String YN = "net/momirealms/sparrow/yaml/node/YamlNode";
    private static final String SEC = "net/momirealms/sparrow/yaml/node/SectionNode";

    private AsmSerializerGenerator() {
    }

    static byte[] generate(
            ClassMeta meta,
            List<ClassMeta.FieldBinding> all,
            List<ClassMeta.FieldBinding> fields,
            ConstructorChoice constructor
    ) {
        String generatedName = GENERATED_PREFIX + ID.incrementAndGet();
        String serializersDesc = "[L" + SER + ";";
        String accessorsDesc = "[L" + FA + ";";
        String instantiatorDesc = "L" + INST + ";";

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC | ACC_FINAL, generatedName, null, "java/lang/Object", new String[]{DEC, ENC});
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "sers", serializersDesc, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "accessors", accessorsDesc, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "instantiator", instantiatorDesc, null, null).visitEnd();

        genInit(writer, generatedName, serializersDesc, accessorsDesc, instantiatorDesc);
        genDec(writer, generatedName, all, fields, constructor);
        genEnc(writer, generatedName, fields);

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void genInit(
            ClassWriter writer,
            String generatedName,
            String serializersDesc,
            String accessorsDesc,
            String instantiatorDesc
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>",
                "([L" + SER + ";[L" + FA + ";L" + INST + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitFieldInsn(PUTFIELD, generatedName, "sers", serializersDesc);

        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 2);
        method.visitFieldInsn(PUTFIELD, generatedName, "accessors", accessorsDesc);

        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 3);
        method.visitFieldInsn(PUTFIELD, generatedName, "instantiator", instantiatorDesc);

        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void genDec(
            ClassWriter writer,
            String generatedName,
            List<ClassMeta.FieldBinding> all,
            List<ClassMeta.FieldBinding> fields,
            ConstructorChoice constructor
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "deserialize", "(L" + YN + ";)Ljava/lang/Object;", null, null);
        method.visitCode();

        Label sectionOk = new Label();
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(INSTANCEOF, SEC);
        method.visitJumpInsn(IFNE, sectionOk);
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);

        method.visitLabel(sectionOk);
        method.visitVarInsn(ALOAD, 1);
        method.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, SEC);
        int sectionVar = 2;
        method.visitVarInsn(ASTORE, sectionVar);

        int argsVar = 3;
        int childVar = 4;
        int decodedVar = 5;
        int instanceVar = 6;

        method.visitLdcInsn(constructor.paramKeys.size());
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        method.visitVarInsn(ASTORE, argsVar);
        emitConstructorArguments(method, generatedName, all, constructor, sectionVar, argsVar, childVar, decodedVar);

        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, generatedName, "instantiator", "L" + INST + ";");
        method.visitVarInsn(ALOAD, argsVar);
        method.visitMethodInsn(INVOKEINTERFACE, INST, "instantiate", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
        method.visitVarInsn(ASTORE, instanceVar);

        injectFields(method, generatedName, fields, constructor, sectionVar, childVar, decodedVar, instanceVar);

        method.visitVarInsn(ALOAD, instanceVar);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitConstructorArguments(
            MethodVisitor method,
            String generatedName,
            List<ClassMeta.FieldBinding> all,
            ConstructorChoice constructor,
            int sectionVar,
            int argsVar,
            int childVar,
            int decodedVar
    ) {
        for (int i = 0; i < constructor.paramKeys.size(); i++) {
            String key = constructor.paramKeys.get(i);
            int serializerIndex = constructor.paramSerIndices.get(i);
            String descriptor = all.get(serializerIndex).field.descriptor;

            method.visitVarInsn(ALOAD, argsVar);
            method.visitLdcInsn(i);
            if (key == null) {
                pushBoxedDefault(method, descriptor);
            } else {
                Label missing = new Label();
                Label done = new Label();
                emitGetChildNode(method, sectionVar, key);
                method.visitVarInsn(ASTORE, childVar);
                method.visitVarInsn(ALOAD, childVar);
                method.visitJumpInsn(IFNULL, missing);
                emitDeserializeValue(method, generatedName, serializerIndex, descriptor, childVar, decodedVar);
                method.visitVarInsn(ALOAD, decodedVar);
                method.visitJumpInsn(GOTO, done);
                method.visitLabel(missing);
                pushBoxedDefault(method, descriptor);
                method.visitLabel(done);
            }
            method.visitInsn(AASTORE);
        }
    }

    private static void injectFields(
            MethodVisitor method,
            String generatedName,
            List<ClassMeta.FieldBinding> fields,
            ConstructorChoice constructor,
            int sectionVar,
            int childVar,
            int decodedVar,
            int instanceVar
    ) {
        for (int i = 0; i < fields.size(); i++) {
            ClassMeta.FieldBinding binding = fields.get(i);
            if (binding.field == null || binding.field.hasYamlIgnore || isConstructorParameter(binding.yamlKey(), constructor)) {
                continue;
            }

            Label skip = new Label();
            emitGetChildNode(method, sectionVar, binding.yamlKey());
            method.visitVarInsn(ASTORE, childVar);
            method.visitVarInsn(ALOAD, childVar);
            method.visitJumpInsn(IFNULL, skip);

            emitDeserializeValue(method, generatedName, binding.index, binding.field.descriptor, childVar, decodedVar);

            emitLoadAccessor(method, generatedName, binding.accessorIndex);
            method.visitVarInsn(ALOAD, instanceVar);
            method.visitVarInsn(ALOAD, decodedVar);
            method.visitMethodInsn(INVOKEINTERFACE, FA, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
            method.visitLabel(skip);
        }
    }

    private static void emitDeserializeValue(
            MethodVisitor method,
            String generatedName,
            int serializerIndex,
            String descriptor,
            int childVar,
            int decodedVar
    ) {
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, generatedName, "sers", "[L" + SER + ";");
        method.visitLdcInsn(serializerIndex);
        method.visitInsn(AALOAD);
        method.visitVarInsn(ALOAD, childVar);
        method.visitMethodInsn(INVOKEVIRTUAL, SER, "deserialize", "(L" + YN + ";)Ljava/lang/Object;", false);
        method.visitVarInsn(ASTORE, decodedVar);

        Label nonNull = new Label();
        Label done = new Label();
        method.visitVarInsn(ALOAD, decodedVar);
        method.visitJumpInsn(IFNONNULL, nonNull);
        pushBoxedDefault(method, descriptor);
        method.visitVarInsn(ASTORE, decodedVar);
        method.visitJumpInsn(GOTO, done);

        method.visitLabel(nonNull);
        method.visitLabel(done);
    }

    private static void genEnc(
            ClassWriter writer,
            String generatedName,
            List<ClassMeta.FieldBinding> fields
    ) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "serialize", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        method.visitCode();

        Label nonNull = new Label();
        method.visitVarInsn(ALOAD, 1);
        method.visitJumpInsn(IFNONNULL, nonNull);
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);

        method.visitLabel(nonNull);
        int valueVar = 2;
        int mapVar = 3;
        int rawValueVar = 4;

        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ASTORE, valueVar);

        int fieldCount = countEncodableFields(fields);
        int mapCapacity = Math.max((int) (fieldCount / 0.75f) + 1, 16);
        method.visitTypeInsn(NEW, "java/util/LinkedHashMap");
        method.visitInsn(DUP);
        method.visitLdcInsn(mapCapacity);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "(I)V", false);
        method.visitVarInsn(ASTORE, mapVar);

        for (int i = 0; i < fields.size(); i++) {
            ClassMeta.FieldBinding binding = fields.get(i);
            if (binding.field == null || binding.field.hasYamlIgnore) {
                continue;
            }
            method.visitVarInsn(ALOAD, mapVar);
            method.visitLdcInsn(binding.yamlKey());

            emitLoadAccessor(method, generatedName, binding.accessorIndex);
            method.visitVarInsn(ALOAD, valueVar);
            method.visitMethodInsn(INVOKEINTERFACE, FA, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            method.visitVarInsn(ASTORE, rawValueVar);

            method.visitVarInsn(ALOAD, 0);
            method.visitFieldInsn(GETFIELD, generatedName, "sers", "[L" + SER + ";");
            method.visitLdcInsn(binding.index);
            method.visitInsn(AALOAD);
            method.visitVarInsn(ALOAD, rawValueVar);
            method.visitMethodInsn(INVOKEVIRTUAL, SER, "serialize", "(Ljava/lang/Object;)Ljava/lang/Object;", false);

            method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            method.visitInsn(POP);
        }

        method.visitVarInsn(ALOAD, mapVar);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

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

    private static boolean isConstructorParameter(String yamlKey, ConstructorChoice constructor) {
        for (int i = 0; i < constructor.paramKeys.size(); i++) {
            if (yamlKey.equals(constructor.paramKeys.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static void emitLoadAccessor(MethodVisitor method, String generatedName, int accessorIndex) {
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, generatedName, "accessors", "[L" + FA + ";");
        method.visitLdcInsn(accessorIndex);
        method.visitInsn(AALOAD);
    }

    private static void pushBoxedDefault(MethodVisitor method, String descriptor) {
        switch (descriptor) {
            case "I" -> {
                method.visitInsn(ICONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            }
            case "J" -> {
                method.visitInsn(LCONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            }
            case "D" -> {
                method.visitInsn(DCONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            }
            case "F" -> {
                method.visitInsn(FCONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            }
            case "Z" -> {
                method.visitInsn(ICONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            }
            case "B" -> {
                method.visitInsn(ICONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            }
            case "S" -> {
                method.visitInsn(ICONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            }
            case "C" -> {
                method.visitInsn(ICONST_0);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            }
            default -> method.visitInsn(ACONST_NULL);
        }
    }

    static final class ConstructorChoice {
        final Constructor<?> constructor;
        final boolean unsafe;
        final List<String> paramKeys;
        final List<Integer> paramSerIndices;

        private ConstructorChoice(
                Constructor<?> constructor,
                boolean unsafe,
                List<String> paramKeys,
                List<Integer> paramSerIndices
        ) {
            this.constructor = constructor;
            this.unsafe = unsafe;
            this.paramKeys = paramKeys;
            this.paramSerIndices = paramSerIndices;
        }

        static ConstructorChoice constructor(Constructor<?> constructor, List<String> paramKeys, List<Integer> paramSerIndices) {
            return new ConstructorChoice(constructor, false, paramKeys, paramSerIndices);
        }

        static ConstructorChoice unsafe() {
            return new ConstructorChoice(null, true, List.of(), List.of());
        }
    }
}
