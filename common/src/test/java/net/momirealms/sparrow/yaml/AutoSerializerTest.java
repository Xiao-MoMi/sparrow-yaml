package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerMode;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.serializer.auto.factory.AsmAutoSerializerFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 SparrowYaml 自动序列化模块的综合测试.
 * 测试用例按功能划分为: Record 类测试、普通 Class 类测试、泛型与 TypeRef 测试、注册表行为测试.
 */
class AutoSerializerTest {

    /**
     * 所有的测试 Mock 模型数据类统一存放在此，便于维护与查看。
     */
    static class Models {

        // ==========================================
        // Record 相关的测试模型
        // ==========================================

        /** 基础的 Record 结构 */
        record BlockPos(int x, int y, int z) {}

        /** 用于验证 ASM 构造器参数默认值的 Record 结构 */
        record AsmPrimitiveDefaults(int count, boolean enabled, long total) {}

        /** 包含嵌套 Record 和集合的复杂结构 */
        record LocationList(List<Location> locations, String name) {}
        record Location(BlockPos pos, double yaw, double pitch) {}

        /** 包含自身递归引用的 Record 结构 */
        record RecursiveNode(String name, RecursiveNode child) {}

        /** 包含被忽略属性和重命名属性的 Record 结构 */
        record IgnoredAndRenamedRecord(
                @YamlProperty("custom_name") String name,
                @YamlIgnore String secret
        ) {}

        // ==========================================
        // 普通 Class 相关的测试模型
        // ==========================================

        /** 使用无参构造函数 + 字段注入的普通 JavaBean */
        static class CustomUser {
            String name;
            int age;

            CustomUser() {}

            CustomUser(String name, int age) {
                this.name = name;
                this.age = age;
            }
        }

        /** 用于验证 ASM 对 category-2 基本类型字段的注入 */
        static class AsmWidePrimitiveFields {
            long total;
            double ratio;

            AsmWidePrimitiveFields() {}
        }

        /** 只有带参构造，且没有注解的不可变类（用于测试外部构造器绑定） */
        static class ExternalUser {
            private final String name;
            private final int age;

            ExternalUser(String name, int age) {
                this.name = name;
                this.age = age;
            }
            public String getName() { return name; }
            public int getAge() { return age; }
        }

        /** 具有多个带参构造器导致歧义的类（用于测试报错机制） */
        static class AmbiguousUser {
            final String name;
            int age;

            AmbiguousUser(String name) { this.name = name; }
            AmbiguousUser(String name, int age) { this.name = name; this.age = age; }
        }

        /** 使用 @YamlConstructor 注解来显式指定反序列化构造器的不可变类 */
        static class ImmutableAnnotatedUser {
            @YamlProperty("user_name")
            private final String name;
            @YamlProperty("user_age")
            private final int age;

            @YamlConstructor
            ImmutableAnnotatedUser(
                    @YamlProperty("user_name") String name,
                    @YamlProperty("user_age") int age) {
                this.name = name;
                this.age = age;
            }

            public String getName() { return name; }
            public int getAge() { return age; }
        }

        /** 构造器会转换参数值，用于验证构造器参数字段不会被二次注入覆盖 */
        static class ConstructorTransformsUser {
            String name;

            @YamlConstructor
            ConstructorTransformsUser(@YamlProperty("name") String name) {
                this.name = name + "_ctor";
            }
        }

        /** 只有无参构造，但字段全部是 private final 的类（用于测试 Field.set fallback） */
        static class FinalFieldInjectionUser {
            private final String name;
            private final int age;

            FinalFieldInjectionUser() {
                this.name = "default";
                this.age = 0;
            }

            public String getName() { return name; }
            public int getAge() { return age; }
        }

        /** 包含泛型字段的类（用于测试 TypeRef） */
        static class Box<T> {
            T value;
            List<T> values;

            Box() {}
            Box(T value, List<T> values) {
                this.value = value;
                this.values = values;
            }
        }

        // ==========================================
        // 异常边缘用例模型
        // ==========================================

        static class RawListContainer {
            @SuppressWarnings("rawtypes")
            List list;
        }

        static class WildcardListContainer {
            List<?> list;
        }

        static class ObjectContainer {
            Object obj;
        }

        interface SomeInterface {}

        abstract class SomeAbstractClass {}

        @FunctionalInterface
        interface SomeFunctionalInterface {
            void run();
        }

        // ==========================================
        // 枚举及集合字段模型
        // ==========================================

        enum Priority { LOW, MEDIUM, HIGH }

        record TaskRecord(String name, Priority priority) {}

        static class TaskClass {
            String name;
            Priority priority;

            TaskClass() {}
        }

        record WithSetRecord(Set<String> tags, String name) {}

        static class SetContainer {
            Set<Integer> numbers;

            SetContainer() {}
        }

        static class NonStringKeyMapContainer {
            Map<Integer, String> map;
        }

        // ==========================================
        // 深度嵌套泛型模型
        // ==========================================

        record DeepNestedMap(Map<String, List<Integer>> scores, String name) {}

        record DeepNestedList(List<Map<String, Integer>> entries) {}

        // ==========================================
        // 继承及构造器边缘用例模型
        // ==========================================

        static class Parent {
            String parentField;
        }

        static class Child extends Parent {
            String childField;

            Child() {}
        }

        static class PrivateParent {
            private String privateParentField;

            String privateParentField() {
                return privateParentField;
            }
        }

        static class PrivateChild extends PrivateParent {
            private String privateChildField;

            PrivateChild() {}

            String privateChildField() {
                return privateChildField;
            }
        }

        static class AnnotatedParent {
            @YamlProperty("parent_name")
            String parentName;
            @YamlIgnore
            String ignoredParent = "default_secret";
            static String staticParent;
            transient String transientParent = "transient_secret";
        }

        static class AnnotatedChild extends AnnotatedParent {
            String childName;

            AnnotatedChild() {}
        }

        static class DuplicateParent {
            @YamlProperty("same")
            String parentName;
        }

        static class DuplicateChild extends DuplicateParent {
            @YamlProperty("same")
            String childName;
        }

        static class GenericParent<T> {
            T parentValue;
            List<T> parentValues;
        }

        static class StringChild extends GenericParent<String> {
            String childValue;

            StringChild() {}
        }

        static class GenericMiddle<U> extends GenericParent<List<U>> {
            U middleValue;
        }

        static class NestedGenericChild extends GenericMiddle<Integer> {
            int childValue;

            NestedGenericChild() {}
        }

        static class ConstructorParent {
            String parentField;
        }

        static class ConstructorChild extends ConstructorParent {
            final String childField;

            @YamlConstructor
            ConstructorChild(@YamlProperty("childField") String childField) {
                this.childField = childField;
            }
        }

        static class UnsafeParent {
            String parentField;
        }

        static class UnsafeChild extends UnsafeParent {
            final String childField;

            UnsafeChild(String childField) {
                this.childField = childField;
            }

            UnsafeChild(String childField, int ignored) {
                this.childField = childField + ignored;
            }
        }

        static class OnlyPrivateConstructor {
            final String data;

            private OnlyPrivateConstructor(String data) {
                this.data = data;
            }
        }

        static class SingleConstructorNoParamNames {
            private final String name;
            private final int value;

            SingleConstructorNoParamNames(String name, int value) {
                this.name = name;
                this.value = value;
            }

            public String getName() { return name; }
            public int getValue() { return value; }
        }

        record FullyIgnoredRecord(
                @YamlIgnore String a,
                @YamlIgnore String b
        ) {}

        static class InterfaceFieldContainer {
            SomeInterface iface;
            InterfaceFieldContainer() {}
        }
    }

    @Nested
    class RecordSerializationTests {

        @Test
        void should_SerializeNestedRecord_When_RegisteredAuto() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.LocationList.class);

            YamlDocument doc = yaml.load("""
                    wrapper:
                      name: "spawn_points"
                      locations:
                        - yaw: 90.0
                          pitch: 15.5
                          pos:
                            x: 100
                            y: 64
                            z: -100
                    """);

            // 2. 执行阶段 (Act)
            Models.LocationList wrapper = doc.get(Models.LocationList.class, "wrapper");

            // 3. 断言阶段 (Assert)
            assertNotNull(wrapper, "反序列化后的对象不应为 null");
            assertEquals("spawn_points", wrapper.name());
            assertEquals(1, wrapper.locations().size());
            assertEquals(100, wrapper.locations().get(0).pos().x());

            // 验证序列化编码是否正确
            doc.setAndGet(Models.LocationList.class, new Models.LocationList(List.of(), "encoded_points"), "new_wrapper");
            Models.LocationList encoded = doc.get(Models.LocationList.class, "new_wrapper");
            assertEquals("encoded_points", encoded.name());
        }

        @Test
        void should_SerializeRecursiveRecord_When_SelfReferenced() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.RecursiveNode.class);

            YamlDocument doc = yaml.load("""
                    node:
                      name: "root"
                      child:
                        name: "child"
                    """);

            // 2. 执行阶段 (Act)
            Models.RecursiveNode node = doc.get(Models.RecursiveNode.class, "node");

            // 3. 断言阶段 (Assert)
            assertNotNull(node);
            assertEquals("root", node.name());
            assertNotNull(node.child(), "递归的子节点应被正确反序列化");
            assertEquals("child", node.child().name());
        }

        @Test
        void should_RespectYamlPropertyAndIgnore_When_AnnotatedOnRecord() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.IgnoredAndRenamedRecord.class);

            YamlDocument doc = yaml.load("""
                    data:
                      custom_name: "Alice"
                      secret: "should_be_ignored"
                    """);

            // 2. 执行阶段 (Act)
            Models.IgnoredAndRenamedRecord record = doc.get(Models.IgnoredAndRenamedRecord.class, "data");

            // 3. 断言阶段 (Assert)
            assertNotNull(record);
            assertEquals("Alice", record.name(), "@YamlProperty 的自定义名称应生效");
            assertNull(record.secret(), "@YamlIgnore 修饰的字段反序列化时应为 null");
        }

        @Test
        void should_SerializeEnumField_When_RecordHasEnum() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.TaskRecord.class);

            YamlDocument doc = yaml.load("""
                    task:
                      name: "review"
                      priority: "HIGH"
                    """);

            Models.TaskRecord task = doc.get(Models.TaskRecord.class, "task");
            assertNotNull(task);
            assertEquals("review", task.name());
            assertEquals(Models.Priority.HIGH, task.priority());
        }

        @Test
        void should_SerializeSetField_When_RecordHasSet() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.WithSetRecord.class);

            YamlDocument doc = yaml.load("""
                    data:
                      name: "tagged"
                      tags:
                        - "a"
                        - "b"
                        - "a"
                    """);

            Models.WithSetRecord record = doc.get(Models.WithSetRecord.class, "data");
            assertNotNull(record);
            assertEquals("tagged", record.name());
            assertEquals(Set.of("a", "b"), record.tags(), "Set 应去重并正确反序列化");
        }

        @Test
        void should_PreserveDefault_When_KeyMissingInRecord() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.BlockPos.class);

            YamlDocument doc = yaml.load("""
                    pos:
                      x: 10
                      z: 30
                    """);

            Models.BlockPos pos = doc.get(Models.BlockPos.class, "pos");
            assertNotNull(pos);
            assertEquals(10, pos.x());
            assertEquals(0, pos.y(), "缺少的 y 键应使用 int 默认值 0");
            assertEquals(30, pos.z());
        }

        @Test
        void should_RoundTrip_When_EncodeThenDecode_Record() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.BlockPos.class);

            Models.BlockPos original = new Models.BlockPos(42, 64, -100);
            YamlDocument doc = yaml.load("");
            doc.setAndGet(Models.BlockPos.class, original, "pos");

            Models.BlockPos decoded = doc.get(Models.BlockPos.class, "pos");
            assertEquals(original, decoded, "往返编码解码后的 Record 应相等");
        }

        @Test
        void should_HandleFullyIgnoredRecord_When_AllComponentsIgnored() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.FullyIgnoredRecord.class);

            YamlDocument doc = yaml.load("""
                    data:
                      a: "ignored"
                      b: "also_ignored"
                    """);

            Models.FullyIgnoredRecord record = doc.get(Models.FullyIgnoredRecord.class, "data");
            assertNotNull(record);
            assertNull(record.a());
            assertNull(record.b());
        }
    }

    @Nested
    class ClassSerializationTests {

        @Test
        void should_InjectViaNoArgsConstructor_When_ClassIsMutable() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.CustomUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      name: "John Doe"
                      age: 30
                    """);

            // 2. 执行阶段 (Act)
            Models.CustomUser user = doc.get(Models.CustomUser.class, "user");

            // 3. 断言阶段 (Assert)
            assertNotNull(user);
            assertEquals("John Doe", user.name, "普通无参构造类字段注入应成功");
            assertEquals(30, user.age);
        }

        @Test
        void should_UseExternalBinding_When_ClassLacksNoArgsConstructor() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            // 手动绑定构造器和参数名
            yaml.serializers().register(Models.ExternalUser.class, binding -> binding
                    .constructor(String.class, int.class)
                    .param("name")
                    .param("age"));

            YamlDocument doc = yaml.load("""
                    user:
                      name: "Jane Doe"
                      age: 25
                    """);

            // 2. 执行阶段 (Act)
            Models.ExternalUser user = doc.get(Models.ExternalUser.class, "user");

            // 3. 断言阶段 (Assert)
            assertNotNull(user);
            assertEquals("Jane Doe", user.getName(), "外部绑定的构造器应被成功调用");
            assertEquals(25, user.getAge());
        }

        @Test
        void should_UseAnnotatedConstructor_When_ImmutableClassHasYamlConstructor() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.ImmutableAnnotatedUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      user_name: "Alice"
                      user_age: 28
                    """);

            // 2. 执行阶段 (Act)
            Models.ImmutableAnnotatedUser user = doc.get(Models.ImmutableAnnotatedUser.class, "user");

            // 3. 断言阶段 (Assert)
            assertNotNull(user);
            assertEquals("Alice", user.getName(), "必须调用带有 @YamlConstructor 的构造器进行实例化");
            assertEquals(28, user.getAge());
        }

        @Test
        void should_NotInjectFieldAgain_When_FieldIsConstructorParameter() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.ConstructorTransformsUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      name: "Alice"
                    """);

            Models.ConstructorTransformsUser user = doc.get(Models.ConstructorTransformsUser.class, "user");

            assertNotNull(user);
            assertEquals("Alice_ctor", user.name, "构造器参数字段不应在实例化后被字段注入覆盖");
        }

        @Test
        void should_InjectFinalFieldsViaFieldSet_When_ClassHasNoArgsConstructor() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.FinalFieldInjectionUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      name: "Final Alice"
                      age: 30
                    """);

            // 2. 执行阶段 (Act)
            Models.FinalFieldInjectionUser user = doc.get(Models.FinalFieldInjectionUser.class, "user");

            // 3. 断言阶段 (Assert)
            assertNotNull(user);
            assertEquals("Final Alice", user.getName(), "Field.set fallback 应能覆盖 private final 字段");
            assertEquals(30, user.getAge());
        }

        @Test
        void should_UseUnsafeFieldInjection_When_ConstructorsAreAmbiguous() throws Exception {
            // 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.AmbiguousUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      name: "Unsafe Alice"
                      age: 31
                    """);

            // 执行阶段 (Act)
            Models.AmbiguousUser user = doc.get(Models.AmbiguousUser.class, "user");

            // 断言阶段 (Assert)
            assertNotNull(user);
            assertEquals("Unsafe Alice", user.name, "存在多个构造器时应使用 Unsafe 创建实例并注入字段");
            assertEquals(31, user.age);
        }

        @Test
        void should_PreserveDefault_When_AmbiguousConstructorFieldMissing() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.AmbiguousUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      name: "Partial Unsafe"
                    """);

            Models.AmbiguousUser user = doc.get(Models.AmbiguousUser.class, "user");
            assertNotNull(user);
            assertEquals("Partial Unsafe", user.name);
            assertEquals(0, user.age, "Unsafe 分配实例后，缺失的 int 字段应保持 JVM 默认值 0");
        }

        @Test
        void should_SerializeEnumField_When_ClassHasEnum() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.TaskClass.class);

            YamlDocument doc = yaml.load("""
                    task:
                      name: "deploy"
                      priority: "LOW"
                    """);

            Models.TaskClass task = doc.get(Models.TaskClass.class, "task");
            assertNotNull(task);
            assertEquals("deploy", task.name);
            assertEquals(Models.Priority.LOW, task.priority);
        }

        @Test
        void should_SerializeSetField_When_ClassHasSet() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.SetContainer.class);

            YamlDocument doc = yaml.load("""
                    data:
                      numbers:
                        - 1
                        - 2
                        - 2
                        - 3
                    """);

            Models.SetContainer container = doc.get(Models.SetContainer.class, "data");
            assertNotNull(container);
            assertEquals(Set.of(1, 2, 3), container.numbers, "Set 应去重并保持整数类型");
        }

        @Test
        void should_PreserveDefault_When_KeyMissingInClass() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.CustomUser.class);

            YamlDocument doc = yaml.load("""
                    user:
                      name: "Partial"
                    """);

            Models.CustomUser user = doc.get(Models.CustomUser.class, "user");
            assertNotNull(user);
            assertEquals("Partial", user.name);
            assertEquals(0, user.age, "缺少的 age 键应使用 int 默认值 0");
        }

        @Test
        void should_RoundTrip_When_EncodeThenDecode_Class() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.CustomUser.class);

            Models.CustomUser original = new Models.CustomUser("RoundTrip", 99);
            YamlDocument doc = yaml.load("");
            doc.setAndGet(Models.CustomUser.class, original, "user");

            Models.CustomUser decoded = doc.get(Models.CustomUser.class, "user");
            assertEquals(original.name, decoded.name);
            assertEquals(original.age, decoded.age);
        }

        @Test
        void should_FailFast_When_OnlyPrivateParamConstructor() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.OnlyPrivateConstructor.class),
                    "仅有私有带参构造器且无法解析参数名时，应抛出异常"
            );
        }

        @Test
        void should_FailFast_When_ConstructorParamNamesUnresolvable() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.SingleConstructorNoParamNames.class),
                    "编译时未保留参数名且无 @YamlProperty 注解，应抛出异常"
            );
        }
    }

    @Nested
    class GenericsAndTypeRefTests {

        @Test
        void should_SerializeGenericClass_When_TypeRefIsProvided() throws Exception {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();
            TypeRef<Models.Box<String>> boxType = new TypeRef<>() {};
            NodeSerializer<Models.Box<String>> serializer = yaml.serializers().register(boxType);

            YamlDocument doc = yaml.load("""
                    box:
                      value: "primary"
                      values:
                        - "first"
                        - "second"
                    """);

            // 2. 执行阶段 (Act)
            Models.Box<String> box = doc.get(boxType, "box");

            // 3. 断言阶段 (Assert)
            assertNotNull(serializer);
            assertNotNull(box);
            assertEquals("primary", box.value);
            assertEquals(List.of("first", "second"), box.values, "泛型列表参数必须正确反序列化");
        }

        @Test
        void should_Fail_When_UnsupportedGenericsAreProvided() {
            // 1. 准备阶段 (Arrange)
            SparrowYaml yaml = SparrowYaml.builder().build();

            // 2 & 3. 执行并断言 (Act & Assert)
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.RawListContainer.class),
                    "不支持 Raw Type 原生类型"
            );
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.WildcardListContainer.class),
                    "不支持 Wildcard 通配符泛型类型"
            );
            
            // Object.class 是默认注册过的，这里验证它能成功通过
            assertNotNull(yaml.serializers().register(Models.ObjectContainer.class));
        }

        @Test
        void should_SerializeNestedBox_When_DoubleWrapped() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            TypeRef<Models.Box<Models.Box<String>>> type = new TypeRef<>() {};
            yaml.serializers().register(type);

            YamlDocument doc = yaml.load("""
                    wrapper:
                      value:
                        value: "deep"
                        values:
                          - "inner"
                    """);

            Models.Box<Models.Box<String>> result = doc.get(type, "wrapper");
            assertNotNull(result);
            assertNotNull(result.value);
            assertEquals("deep", result.value.value);
            assertEquals(List.of("inner"), result.value.values);
        }

        @Test
        void should_SerializeNestedMapList_When_DeeplyNested() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.DeepNestedMap.class);

            YamlDocument doc = yaml.load("""
                    data:
                      name: "scores"
                      scores:
                        alice:
                          - 95
                          - 87
                        bob:
                          - 72
                    """);

            Models.DeepNestedMap result = doc.get(Models.DeepNestedMap.class, "data");
            assertNotNull(result);
            assertEquals("scores", result.name());
            assertEquals(Map.of("alice", List.of(95, 87), "bob", List.of(72)), result.scores());
        }

        @Test
        void should_SerializeNestedListMap_When_DeeplyNested() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.DeepNestedList.class);

            YamlDocument doc = yaml.load("""
                    data:
                      entries:
                        - alice: 100
                          bob: 90
                        - carol: 80
                    """);

            Models.DeepNestedList result = doc.get(Models.DeepNestedList.class, "data");
            assertNotNull(result);
            assertEquals(2, result.entries().size());
            assertEquals(Map.of("alice", 100, "bob", 90), result.entries().get(0));
            assertEquals(Map.of("carol", 80), result.entries().get(1));
        }
    }

    @Nested
    class EdgeCaseTests {
        @Test
        void should_FailFast_When_TargetIsInterface() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.SomeInterface.class),
                    "未注册序列化器时，接口应快速失败并提示手动注册"
            );
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.SomeFunctionalInterface.class),
                    "未注册序列化器时，函数式接口应快速失败并提示手动注册"
            );
        }

        @Test
        void should_FailFast_When_TargetIsAbstractClass() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.SomeAbstractClass.class),
                    "未注册序列化器时，抽象类应快速失败并提示手动注册"
            );
        }

        @Test
        void should_FailFast_When_TargetIsArray() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(String[].class),
                    "未注册序列化器时，数组类型应快速失败并提示手动注册"
            );
        }

        @Test
        void should_UseManuallyRegisteredSerializer_When_InterfaceField() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            // 先手动注册接口的序列化器
            yaml.serializers().register(Models.SomeInterface.class, NodeSerializers.STRING.xmap(
                    ignored -> new Models.SomeInterface() {},
                    ignored -> "some_impl"
            ));
            // 注册包含该接口字段的类应不再抛出异常
            assertDoesNotThrow(
                    () -> yaml.serializers().register(Models.InterfaceFieldContainer.class),
                    "手动注册接口序列化器后，auto 应能使用它"
            );
        }

        @Test
        void should_FailFast_When_NonStringMapKey() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            assertThrows(AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.NonStringKeyMapContainer.class),
                    "Map 的键不是 String 时应抛出异常"
            );
        }

        @Test
        void should_CollectParentFields_When_InheritanceIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.Child.class);

            YamlDocument doc = yaml.load("""
                    data:
                      childField: "child_value"
                      parentField: "parent_value"
                    """);

            Models.Child child = doc.get(Models.Child.class, "data");
            assertNotNull(child);
            assertEquals("parent_value", child.parentField);
            assertEquals("child_value", child.childField);

            Models.Child encoded = new Models.Child();
            encoded.parentField = "encoded_parent";
            encoded.childField = "encoded_child";
            doc.setAndGet(Models.Child.class, encoded, "encoded");

            assertEquals("encoded_parent", doc.get(String.class, "encoded", "parentField"));
            assertEquals("encoded_child", doc.get(String.class, "encoded", "childField"));
        }

        @Test
        void should_RespectParentYamlPropertyIgnoreStaticAndTransientFields() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.AnnotatedChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parent_name: "parent"
                      ignoredParent: "secret"
                      staticParent: "static"
                      transientParent: "transient"
                      childName: "child"
                    """);

            Models.AnnotatedChild child = doc.get(Models.AnnotatedChild.class, "data");
            assertNotNull(child);
            assertEquals("parent", child.parentName);
            assertEquals("default_secret", child.ignoredParent);
            assertEquals("transient_secret", child.transientParent);
            assertEquals("child", child.childName);

            doc.setAndGet(Models.AnnotatedChild.class, child, "encoded");
            assertEquals("parent", doc.get(String.class, "encoded", "parent_name"));
            assertEquals("child", doc.get(String.class, "encoded", "childName"));
            assertNull(doc.getNodeOrNull("encoded", "ignoredParent"));
            assertNull(doc.getNodeOrNull("encoded", "staticParent"));
            assertNull(doc.getNodeOrNull("encoded", "transientParent"));
        }

        @Test
        void should_FailFast_When_ParentAndChildUseSameYamlKey() {
            SparrowYaml yaml = SparrowYaml.builder().build();

            AutoSerializerException exception = assertThrows(
                    AutoSerializerException.class,
                    () -> yaml.serializers().register(Models.DuplicateChild.class)
            );
            assertTrue(exception.getMessage().contains("Duplicate YAML field 'same'"));
        }

        @Test
        void should_ResolveParentGenericFields_When_ChildBindsTypeVariable() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.StringChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentValue: "parent"
                      parentValues:
                        - "one"
                        - "two"
                      childValue: "child"
                    """);

            Models.StringChild child = doc.get(Models.StringChild.class, "data");
            assertNotNull(child);
            assertEquals("parent", child.parentValue);
            assertEquals(List.of("one", "two"), child.parentValues);
            assertEquals("child", child.childValue);
        }

        @Test
        void should_ResolveMultiLevelGenericParentFields() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.NestedGenericChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentValue:
                        - 1
                        - 2
                      parentValues:
                        - - 3
                          - 4
                        - - 5
                      middleValue: 6
                      childValue: 7
                    """);

            Models.NestedGenericChild child = doc.get(Models.NestedGenericChild.class, "data");
            assertNotNull(child);
            assertEquals(List.of(1, 2), child.parentValue);
            assertEquals(List.of(List.of(3, 4), List.of(5)), child.parentValues);
            assertEquals(6, child.middleValue);
            assertEquals(7, child.childValue);
        }

        @Test
        void should_InjectParentFields_When_ChildUsesYamlConstructor() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.ConstructorChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentField: "parent"
                      childField: "child"
                    """);

            Models.ConstructorChild child = doc.get(Models.ConstructorChild.class, "data");
            assertNotNull(child);
            assertEquals("parent", child.parentField);
            assertEquals("child", child.childField);
        }

        @Test
        void should_InjectParentFields_When_UnsafeInstantiationIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.UnsafeChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentField: "parent"
                      childField: "child"
                    """);

            Models.UnsafeChild child = doc.get(Models.UnsafeChild.class, "data");
            assertNotNull(child);
            assertEquals("parent", child.parentField);
            assertEquals("child", child.childField);
        }

        @Test
        void should_RoundTripSet_When_EncodeThenDecode() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.WithSetRecord.class);

            Models.WithSetRecord original = new Models.WithSetRecord(Set.of("x", "y"), "set_test");
            YamlDocument doc = yaml.load("");
            doc.setAndGet(Models.WithSetRecord.class, original, "data");

            Models.WithSetRecord decoded = doc.get(Models.WithSetRecord.class, "data");
            assertEquals(original.tags(), decoded.tags());
            assertEquals(original.name(), decoded.name());
        }

        @Test
        void should_HandleNullField_When_Encoding() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            yaml.serializers().register(Models.CustomUser.class);

            Models.CustomUser userWithNullName = new Models.CustomUser();
            userWithNullName.age = 42;
            YamlDocument doc = yaml.load("");
            doc.setAndGet(Models.CustomUser.class, userWithNullName, "user");

            Models.CustomUser decoded = doc.get(Models.CustomUser.class, "user");
            assertNotNull(decoded);
            assertNull(decoded.name, "null 字段应正确往返");
            assertEquals(42, decoded.age);
        }
    }

    @Nested
    class RegistryBehaviorTests {

        @Test
        void should_NotOverride_When_ManualSerializerExists() {
            SparrowYaml yaml = SparrowYaml.builder().build();
            
            // 手动注册一个自定义的拦截序列化器
            NodeSerializer<Models.CustomUser> customSerializer = NodeSerializers.STRING.xmap(
                    ignored -> new Models.CustomUser("Custom", 999),
                    ignored -> "CustomString"
            );
            yaml.serializers().register(Models.CustomUser.class, customSerializer);

            // 尝试触发自动生成
            NodeSerializer<Models.CustomUser> autoSerializer = yaml.serializers().register(Models.CustomUser.class);
            assertEquals(customSerializer, autoSerializer, "如果用户已经手动注册，则 register 应直接返回现有实例而不做覆盖");
        }
    }

    @Nested
    class AutoSerializerModeTests {

        @Test
        void should_UseAdaptiveDefaultAcrossIsolatedClassLoader(@TempDir Path tempDir) throws Exception {
            Class<?> type = compileAndLoadExternal(tempDir, "external.ExternalPojo", """
                    package external;

                    public class ExternalPojo {
                        public String name;
                        public int amount;

                        public ExternalPojo() {
                        }
                    }
                    """);
            assertCannotSeeSparrowYaml(type);

            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Object> serializer = registerRaw(yaml, type);

            YamlDocument doc = yaml.load("""
                    data:
                      name: "adaptive"
                      amount: 17
                    """);

            Object result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("adaptive", type.getField("name").get(result));
            assertEquals(17, type.getField("amount").get(result));
        }

        @Test
        void should_UseBridgeAsmForPrivateFieldsAcrossIsolatedClassLoader(@TempDir Path tempDir) throws Exception {
            Class<?> type = compileAndLoadExternal(tempDir, "external.ExternalPrivatePojo", """
                    package external;

                    public class ExternalPrivatePojo {
                        private String name;
                        private int amount;

                        public ExternalPrivatePojo() {
                        }

                        public String name() {
                            return name;
                        }

                        public int amount() {
                            return amount;
                        }
                    }
                    """);
            assertCannotSeeSparrowYaml(type);

            SparrowYaml yaml = SparrowYaml.builder()
                    .setAutoSerializerMode(AutoSerializerMode.ASM)
                    .build();
            NodeSerializer<Object> serializer = registerRaw(yaml, type);

            YamlDocument doc = yaml.load("""
                    data:
                      name: "bridge"
                      amount: 42
                    """);

            Object result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("bridge", type.getMethod("name").invoke(result));
            assertEquals(42, type.getMethod("amount").invoke(result));
        }

        @Test
        void should_UseReflectionModeWhenRequested(@TempDir Path tempDir) throws Exception {
            Class<?> type = compileAndLoadExternal(tempDir, "external.ExternalReflectionPojo", """
                    package external;

                    public class ExternalReflectionPojo {
                        public String name;

                        public ExternalReflectionPojo() {
                        }
                    }
                    """);

            SparrowYaml yaml = SparrowYaml.builder()
                    .setAutoSerializerMode(AutoSerializerMode.REFLECTION)
                    .build();
            NodeSerializer<Object> serializer = registerRaw(yaml, type);

            YamlDocument doc = yaml.load("""
                    data:
                      name: "reflection"
                    """);

            Object result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("reflection", type.getField("name").get(result));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private NodeSerializer<Object> registerRaw(SparrowYaml yaml, Class<?> type) {
            return (NodeSerializer<Object>) yaml.serializers().register((Class) type);
        }

        private void assertCannotSeeSparrowYaml(Class<?> type) {
            assertThrows(ClassNotFoundException.class, () ->
                    Class.forName(NodeSerializer.class.getName(), false, type.getClassLoader()));
        }

        private Class<?> compileAndLoadExternal(Path tempDir, String binaryName, String source) throws Exception {
            Path sourceFile = tempDir.resolve(binaryName.replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler, "Tests require a JDK, not a JRE");
            int compileResult = compiler.run(null, null, null, "-d", tempDir.toString(), sourceFile.toString());
            assertEquals(0, compileResult);

            URLClassLoader loader = new URLClassLoader(
                    new URL[]{tempDir.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader()
            );
            return Class.forName(binaryName, true, loader);
        }
    }

    @Nested
    class AsmFactoryTests {

        @Test
        void should_UsePrimitiveDefault_When_AsmConstructorKeyMissing() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.AsmPrimitiveDefaults> serializer = createAsmSerializer(
                    yaml,
                    Models.AsmPrimitiveDefaults.class
            );

            YamlDocument doc = yaml.load("""
                    data:
                      count: 7
                    """);

            Models.AsmPrimitiveDefaults result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals(7, result.count());
            assertFalse(result.enabled());
            assertEquals(0L, result.total());
        }

        @Test
        void should_InjectWidePrimitiveFields_When_AsmSerializerIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.AsmWidePrimitiveFields> serializer = createAsmSerializer(
                    yaml,
                    Models.AsmWidePrimitiveFields.class
            );

            YamlDocument doc = yaml.load("""
                    data:
                      total: 922337203685477580
                      ratio: 12.5
                    """);

            Models.AsmWidePrimitiveFields result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals(922337203685477580L, result.total);
            assertEquals(12.5D, result.ratio);
        }

        @Test
        void should_CollectParentFields_When_AsmSerializerIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.Child> serializer = createAsmSerializer(yaml, Models.Child.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentField: "parent"
                      childField: "child"
                    """);

            Models.Child result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("parent", result.parentField);
            assertEquals("child", result.childField);

            Object encodedNode = serializer.serialize(result);
            assertTrue(encodedNode instanceof Map<?, ?>);
            Map<?, ?> encoded = (Map<?, ?>) encodedNode;
            assertEquals("parent", encoded.get("parentField"));
            assertEquals("child", encoded.get("childField"));
        }

        @Test
        void should_AccessPrivateParentFields_When_AsmSerializerIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.PrivateChild> serializer = createAsmSerializer(yaml, Models.PrivateChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      privateParentField: "parent"
                      privateChildField: "child"
                    """);

            Models.PrivateChild result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("parent", result.privateParentField());
            assertEquals("child", result.privateChildField());

            Object encodedNode = serializer.serialize(result);
            assertTrue(encodedNode instanceof Map<?, ?>);
            Map<?, ?> encoded = (Map<?, ?>) encodedNode;
            assertEquals("parent", encoded.get("privateParentField"));
            assertEquals("child", encoded.get("privateChildField"));
        }

        @Test
        void should_RespectParentAnnotations_When_AsmSerializerIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.AnnotatedChild> serializer = createAsmSerializer(yaml, Models.AnnotatedChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parent_name: "parent"
                      ignoredParent: "ignored"
                      staticParent: "static"
                      transientParent: "transient"
                      childName: "child"
                    """);

            Models.AnnotatedChild result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("parent", result.parentName);
            assertEquals("default_secret", result.ignoredParent);
            assertEquals("transient_secret", result.transientParent);
            assertEquals("child", result.childName);

            Object encodedNode = serializer.serialize(result);
            assertTrue(encodedNode instanceof Map<?, ?>);
            Map<?, ?> encoded = (Map<?, ?>) encodedNode;
            assertEquals("parent", encoded.get("parent_name"));
            assertEquals("child", encoded.get("childName"));
            assertFalse(encoded.containsKey("ignoredParent"));
            assertFalse(encoded.containsKey("staticParent"));
            assertFalse(encoded.containsKey("transientParent"));
        }

        @Test
        void should_FailFast_When_AsmParentAndChildUseSameYamlKey() {
            SparrowYaml yaml = SparrowYaml.builder().build();

            AutoSerializerException exception = assertThrows(
                    AutoSerializerException.class,
                    () -> createAsmSerializer(yaml, Models.DuplicateChild.class)
            );
            assertTrue(exception.getMessage().contains("Duplicate YAML field 'same'"));
        }

        @Test
        void should_ResolveParentGenericFields_When_AsmSerializerIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.StringChild> serializer = createAsmSerializer(yaml, Models.StringChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentValue: "parent"
                      parentValues:
                        - "one"
                        - "two"
                      childValue: "child"
                    """);

            Models.StringChild result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("parent", result.parentValue);
            assertEquals(List.of("one", "two"), result.parentValues);
            assertEquals("child", result.childValue);
        }

        @Test
        void should_ResolveMultiLevelGenericParentFields_When_AsmSerializerIsUsed() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.NestedGenericChild> serializer = createAsmSerializer(
                    yaml,
                    Models.NestedGenericChild.class
            );

            YamlDocument doc = yaml.load("""
                    data:
                      parentValue:
                        - 1
                        - 2
                      parentValues:
                        - - 3
                          - 4
                        - - 5
                      middleValue: 6
                      childValue: 7
                    """);

            Models.NestedGenericChild result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals(List.of(1, 2), result.parentValue);
            assertEquals(List.of(List.of(3, 4), List.of(5)), result.parentValues);
            assertEquals(6, result.middleValue);
            assertEquals(7, result.childValue);
        }

        @Test
        void should_InjectParentFields_When_AsmChildUsesYamlConstructor() throws Exception {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<Models.ConstructorChild> serializer = createAsmSerializer(yaml, Models.ConstructorChild.class);

            YamlDocument doc = yaml.load("""
                    data:
                      parentField: "parent"
                      childField: "child"
                    """);

            Models.ConstructorChild result = serializer.deserialize(doc.getNodeOrNull("data"));

            assertNotNull(result);
            assertEquals("parent", result.parentField);
            assertEquals("child", result.childField);
        }

        private <T> NodeSerializer<T> createAsmSerializer(SparrowYaml yaml, Class<T> type) {
            SerializerRegistry registry = new SerializerRegistry(yaml);
            AsmAutoSerializerFactory factory = new AsmAutoSerializerFactory();
            return factory.create(type, registry, new AutoSerializerBinding());
        }
    }
}
