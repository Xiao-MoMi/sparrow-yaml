package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class NodeConvenienceReadTest {
    private static final String YAML = """
            server:
              host: localhost
              port: "25565"
              enabled: true
              timeout: 1234567890123
              float-ratio: 1.25
              ratio: 1.5
            servers:
              - host: indexed
                port: "24444"
            null-value: null
            quoted-null: "null"
            invalid-int: nope
            enum-value: sEcOnD
            invalid-enum: unknown
            admins: [alice, 2]
            numbers: [1, "2"]
            not-list: value
            invalid-list: [one, {name: two}]
            empty-list: []
            typed-maps:
              - alpha: 1
              - beta: "2"
            empty-map-list: []
            invalid-map-list:
              - alpha: 1
              - invalid
            scalar-number: "12"
            scalar-boolean: true
            literal:
              key: nested
            literal.key: literal
            lists:
              strings: [one, 2]
              ints: [1, "2"]
              doubles: [1, "2"]
              floats: [1, "2"]
              longs: [1, "2"]
              booleans: [true, "false"]
              scalars: [1, two, true]
              objects: [text, {nested: value}, [1, 2]]
            """;

    @Nested
    class ParentNodeReadTests {

        @Test
        void should_ReadCommonTypesFromExactRoutes() throws IOException {
            YamlDocument document = document();

            assertEquals("localhost", document.getString(Route.from("server", "host")));
            assertEquals(25565, document.getInt(Route.from("server", "port")));
            assertTrue(document.getBoolean(Route.from("server", "enabled")));
            assertEquals(1234567890123L, document.getLong(Route.from("server", "timeout")));
            assertEquals(1.25F, document.getFloat(Route.from("server", "float-ratio")));
            assertEquals(1.5D, document.getDouble(Route.from("server", "ratio")));
            assertEquals(TestMode.SECOND, document.getEnum(TestMode.class, Route.from("enum-value")));
            assertEquals("indexed", document.getString(Route.from("servers", 0, "host")));
            assertEquals(24444, document.getInt(Route.from("servers", 0, "port")));
        }

        @Test
        void should_UseDefaultsOnlyForMissingAndYamlNull() throws IOException {
            YamlDocument document = document();

            assertNull(document.getString(Route.from("missing")));
            assertEquals("fallback", document.getStringOrDefault("fallback", Route.from("missing")));
            assertFalse(document.getBoolean(Route.from("missing")));
            assertTrue(document.getBooleanOrDefault(true, Route.from("missing")));
            assertEquals(0, document.getInt(Route.from("missing")));
            assertEquals(7, document.getIntOrDefault(7, Route.from("null-value")));
            assertEquals(0L, document.getLong(Route.from("missing")));
            assertEquals(7L, document.getLongOrDefault(7L, Route.from("missing")));
            assertEquals(0F, document.getFloat(Route.from("missing")));
            assertEquals(7F, document.getFloatOrDefault(7F, Route.from("missing")));
            assertEquals(0D, document.getDouble(Route.from("missing")));
            assertEquals(7D, document.getDoubleOrDefault(7D, Route.from("missing")));
            assertNull(document.getEnum(TestMode.class, Route.from("missing")));
            assertEquals(TestMode.FIRST, document.getEnumOrDefault(TestMode.FIRST, TestMode.class, Route.from("missing")));
            assertEquals(TestMode.FIRST, document.getEnumOrDefault(TestMode.FIRST, TestMode.class, Route.from("null-value")));

            assertThrows(InvalidNodeException.class, () -> document.getIntOrDefault(7, Route.from("invalid-int")));
            assertThrows(InvalidNodeException.class, () -> document.getIntOrDefault(7, Route.from("quoted-null")));
            assertThrows(InvalidNodeException.class, () -> document.getEnumOrDefault(TestMode.FIRST, TestMode.class, Route.from("invalid-enum")));
        }

        @Test
        void should_InvokeParentSuppliersOnlyWhenDefaultIsNeeded() throws IOException {
            YamlDocument document = document();
            AtomicInteger calls = new AtomicInteger();
            Supplier<Integer> defaultValueSupplier = () -> {
                calls.incrementAndGet();
                return 7;
            };

            assertEquals(25565, document.getIntOrDefault(defaultValueSupplier, Route.from("server", "port")));
            assertEquals(0, calls.get());
            assertEquals(7, document.getIntOrDefault(defaultValueSupplier, Route.from("missing")));
            assertEquals(7, document.getIntOrDefault(defaultValueSupplier, Route.from("null-value")));
            assertEquals(2, calls.get());

            assertEquals("fallback", document.getStringOrDefault(() -> "fallback", Route.from("missing")));
            assertTrue(document.getBooleanOrDefault(() -> true, Route.from("missing")));
            assertEquals(7L, document.getLongOrDefault(() -> 7L, Route.from("missing")));
            assertEquals(7F, document.getFloatOrDefault(() -> 7F, Route.from("missing")));
            assertEquals(7D, document.getDoubleOrDefault(() -> 7D, Route.from("missing")));
            assertEquals(TestMode.FIRST, document.getEnumOrDefault(() -> TestMode.FIRST, TestMode.class, Route.from("missing")));

            assertThrows(InvalidNodeException.class, () -> document.getIntOrDefault(defaultValueSupplier, Route.from("invalid-int")));
            assertEquals(2, calls.get());
        }

        @Test
        void should_KeepGenericDefaultsLazyAndMissingOnly() throws IOException {
            YamlDocument document = document();
            TypeRef<Integer> integerType = new TypeRef<>() {};
            AtomicInteger calls = new AtomicInteger();
            Supplier<Integer> defaultValueSupplier = () -> {
                calls.incrementAndGet();
                return 7;
            };

            assertEquals(25565, document.getOrDefault(defaultValueSupplier, NodeSerializers.INT, Route.from("server", "port")));
            assertEquals(0, calls.get());
            assertEquals(7, document.getOrDefault(7, NodeSerializers.INT, Route.from("missing")));
            assertEquals(7, document.getOrDefault(7, Integer.class, Route.from("missing")));
            assertEquals(7, document.getOrDefault(7, integerType, Route.from("missing")));
            assertEquals(7, document.getOrDefault(defaultValueSupplier, NodeSerializers.INT, Route.from("missing")));
            assertEquals(7, document.getOrDefault(defaultValueSupplier, Integer.class, Route.from("missing")));
            assertEquals(7, document.getOrDefault(defaultValueSupplier, integerType, Route.from("missing")));
            assertEquals(3, calls.get());

            assertThrows(InvalidNodeException.class, () -> document.getOrDefault(defaultValueSupplier, Integer.class, Route.from("null-value")));
            assertThrows(InvalidNodeException.class, () -> document.getOrDefault(defaultValueSupplier, Integer.class, Route.from("invalid-int")));
            assertEquals(3, calls.get());
        }

        @Test
        void should_ReadListsStrictly() throws IOException {
            YamlDocument document = document();
            TypeRef<Integer> integerType = new TypeRef<>() {};

            assertEquals(List.of("alice", "2"), document.getList(String.class, Route.from("admins")));
            assertEquals(List.of(1, 2), document.getList(NodeSerializers.INT, Route.from("numbers")));
            assertEquals(List.of(1, 2), document.getList(Integer.class, Route.from("numbers")));
            assertEquals(List.of(1, 2), document.getList(integerType, Route.from("numbers")));
            assertEquals(List.of(), document.getList(NodeSerializers.INT, Route.from("empty-list")));
            assertEquals(List.of(), document.getList(Integer.class, Route.from("empty-list")));
            assertEquals(List.of(), document.getList(integerType, Route.from("empty-list")));
            assertNull(document.getList(String.class, Route.from("missing")));
            assertNull(document.getList(String.class, Route.from("null-value")));
            assertNull(document.getList(NodeSerializers.INT, Route.from("missing")));
            assertNull(document.getList(Integer.class, Route.from("missing")));
            assertNull(document.getList(integerType, Route.from("missing")));

            assertThrows(InvalidNodeException.class, () -> document.getList(String.class, Route.from("not-list")));
            assertThrows(InvalidNodeException.class, () -> document.getList(String.class, Route.from("invalid-list")));
            assertThrows(InvalidNodeException.class, () -> document.getList(Integer.class, Route.from("not-list")));
            assertThrows(InvalidNodeException.class, () -> document.getList(integerType, Route.from("not-list")));
        }

        @Test
        void should_ExposeExistenceSectionsAndRepresentedValues() throws IOException {
            YamlDocument document = document();

            assertTrue(document.contains(Route.from("null-value")));
            assertFalse(document.contains(Route.from("missing")));
            assertNotNull(document.getSectionOrNull(Route.from("server")));
            assertNotNull(document.getSectionOrNull(Route.from("servers", 0)));
            assertNull(document.getSectionOrNull(Route.from("server", "host")));
            assertNull(document.getValue(Route.from("null-value")));
            assertNull(document.getValue(Route.from("missing")));

            Map<?, ?> server = assertInstanceOf(Map.class, document.getValue(Route.from("server")));
            assertEquals("localhost", server.get("host"));
        }

        @Test
        void should_PreserveLiteralRouteBehavior() throws IOException {
            YamlDocument document = document();

            assertEquals("nested", document.get(String.class, Route.from("literal", "key")));
            assertEquals("literal", document.getString(Route.from("literal.key")));
        }

        @Test
        void should_PreferRegisteredEnumSerializer() throws IOException {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<TestMode> serializer = NodeSerializers.STRING.xmap(TestMode.class, ignored -> TestMode.FIRST, TestMode::name);
            yaml.serializers().register(TestMode.class, serializer);
            YamlDocument document = yaml.load(YAML);

            assertEquals(TestMode.FIRST, document.getEnum(TestMode.class, Route.from("enum-value")));
            assertEquals(TestMode.FIRST, document.getScalarOrNull("enum-value").getEnum(TestMode.class));
        }
    }

    @Nested
    class ScalarNodeReadTests {

        @Test
        void should_ReadScalarValuesWithRegisteredSerializers() throws IOException {
            YamlDocument document = document();
            ScalarNode number = document.getScalarOrNull("scalar-number");
            ScalarNode bool = document.getScalarOrNull("scalar-boolean");
            ScalarNode mode = document.getScalarOrNull("enum-value");

            assertEquals("12", number.getString());
            assertTrue(number.getBoolean());
            assertEquals(12, number.getInt());
            assertEquals(12L, number.getLong());
            assertEquals(12F, number.getFloat());
            assertEquals(12D, number.getDouble());
            assertTrue(bool.getBoolean());
            assertEquals(TestMode.SECOND, mode.getEnum(TestMode.class));
        }

        @Test
        void should_UseScalarDefaultsOnlyForYamlNull() throws IOException {
            YamlDocument document = document();
            ScalarNode nullNode = document.getScalarOrNull("null-value");
            ScalarNode invalid = document.getScalarOrNull("invalid-int");
            ScalarNode invalidEnum = document.getScalarOrNull("invalid-enum");

            assertNull(nullNode.getString());
            assertEquals("fallback", nullNode.getStringOrDefault("fallback"));
            assertFalse(nullNode.getBoolean());
            assertTrue(nullNode.getBooleanOrDefault(true));
            assertEquals(0, nullNode.getInt());
            assertEquals(7, nullNode.getIntOrDefault(7));
            assertEquals(0L, nullNode.getLong());
            assertEquals(7L, nullNode.getLongOrDefault(7L));
            assertEquals(0F, nullNode.getFloat());
            assertEquals(7F, nullNode.getFloatOrDefault(7F));
            assertEquals(0D, nullNode.getDouble());
            assertEquals(7D, nullNode.getDoubleOrDefault(7D));
            assertNull(nullNode.getEnum(TestMode.class));
            assertEquals(TestMode.FIRST, nullNode.getEnumOrDefault(TestMode.FIRST, TestMode.class));

            assertThrows(InvalidNodeException.class, () -> invalid.getIntOrDefault(7));
            assertThrows(InvalidNodeException.class, () -> invalidEnum.getEnumOrDefault(TestMode.FIRST, TestMode.class));
        }

        @Test
        void should_InvokeScalarSuppliersOnlyForYamlNull() throws IOException {
            YamlDocument document = document();
            ScalarNode number = document.getScalarOrNull("scalar-number");
            ScalarNode nullNode = document.getScalarOrNull("null-value");
            ScalarNode invalid = document.getScalarOrNull("invalid-int");
            AtomicInteger calls = new AtomicInteger();
            Supplier<Integer> defaultValueSupplier = () -> {
                calls.incrementAndGet();
                return 7;
            };

            assertEquals(12, number.getIntOrDefault(defaultValueSupplier));
            assertEquals(0, calls.get());
            assertEquals(7, nullNode.getIntOrDefault(defaultValueSupplier));
            assertEquals(1, calls.get());

            assertEquals("fallback", nullNode.getStringOrDefault(() -> "fallback"));
            assertTrue(nullNode.getBooleanOrDefault(() -> true));
            assertEquals(7L, nullNode.getLongOrDefault(() -> 7L));
            assertEquals(7F, nullNode.getFloatOrDefault(() -> 7F));
            assertEquals(7D, nullNode.getDoubleOrDefault(() -> 7D));
            assertEquals(TestMode.FIRST, nullNode.getEnumOrDefault(() -> TestMode.FIRST, TestMode.class));

            assertThrows(InvalidNodeException.class, () -> invalid.getIntOrDefault(defaultValueSupplier));
            assertEquals(1, calls.get());
        }
    }

    @Nested
    class MapListTests {

        @Test
        void should_ReadMapListStrictlyWithRegisteredClasses() throws IOException {
            YamlDocument document = document();
            TypeRef<String> keyType = new TypeRef<>() {};
            TypeRef<Integer> valueType = new TypeRef<>() {};

            List<Map<String, Integer>> maps = document.getMapList(String.class, Integer.class, Route.from("typed-maps"));
            List<Map<String, Integer>> mapsByTypeRef = document.getMapList(keyType, valueType, Route.from("typed-maps"));

            assertEquals(List.of(Map.of("alpha", 1), Map.of("beta", 2)), maps);
            assertEquals(maps, mapsByTypeRef);
            assertEquals(List.of(), document.getMapList(String.class, Integer.class, Route.from("empty-map-list")));
            assertEquals(List.of(), document.getMapList(keyType, valueType, Route.from("empty-map-list")));
            assertNull(document.getMapList(String.class, Integer.class, Route.from("missing")));
            assertNull(document.getMapList(String.class, Integer.class, Route.from("null-value")));
            assertNull(document.getMapList(keyType, valueType, Route.from("missing")));
            assertNull(document.getMapList(keyType, valueType, Route.from("null-value")));
            assertThrows(InvalidNodeException.class, () -> document.getMapList(String.class, Integer.class, Route.from("not-list")));
            assertThrows(InvalidNodeException.class, () -> document.getMapList(keyType, valueType, Route.from("not-list")));
            assertThrows(InvalidNodeException.class, () -> document.getMapList(String.class, Integer.class, Route.from("invalid-map-list")));
            assertThrows(InvalidNodeException.class, () -> document.getMapList(keyType, valueType, Route.from("invalid-map-list")));
        }

        @Test
        void should_ReadMapListWithCustomClassesAndSerializers() throws IOException {
            SparrowYaml yaml = SparrowYaml.builder().build();
            NodeSerializer<MapKey> keySerializer = NodeSerializers.STRING.xmap(MapKey.class, MapKey::new, MapKey::value);
            NodeSerializer<MapValue> valueSerializer = NodeSerializers.INT.xmap(MapValue.class, MapValue::new, MapValue::value);
            yaml.serializers().register(MapKey.class, keySerializer);
            yaml.serializers().register(MapValue.class, valueSerializer);
            YamlDocument document = yaml.load(YAML);
            TypeRef<MapKey> keyType = new TypeRef<>() {};
            TypeRef<MapValue> valueType = new TypeRef<>() {};

            List<Map<MapKey, MapValue>> byClass = document.getMapList(MapKey.class, MapValue.class, Route.from("typed-maps"));
            List<Map<MapKey, MapValue>> byTypeRef = document.getMapList(keyType, valueType, Route.from("typed-maps"));
            List<Map<MapKey, MapValue>> bySerializer = document.getMapList(keySerializer, valueSerializer, Route.from("typed-maps"));

            List<Map<MapKey, MapValue>> expected = List.of(
                    Map.of(new MapKey("alpha"), new MapValue(1)),
                    Map.of(new MapKey("beta"), new MapValue(2))
            );
            assertEquals(expected, byClass);
            assertEquals(expected, byTypeRef);
            assertEquals(expected, bySerializer);
            assertEquals(List.of(), document.getMapList(keySerializer, valueSerializer, Route.from("empty-map-list")));
            assertNull(document.getMapList(keySerializer, valueSerializer, Route.from("missing")));
        }
    }

    @Test
    void should_ExposeReusableBasicListSerializers() throws IOException {
        YamlDocument document = document();

        assertEquals(List.of("one", "2"), document.get(NodeSerializers.STRING_LIST, Route.from("lists", "strings")));
        assertEquals(List.of(1, 2), document.get(NodeSerializers.INT_LIST, Route.from("lists", "ints")));
        assertEquals(List.of(1D, 2D), document.get(NodeSerializers.DOUBLE_LIST, Route.from("lists", "doubles")));
        assertEquals(List.of(1F, 2F), document.get(NodeSerializers.FLOAT_LIST, Route.from("lists", "floats")));
        assertEquals(List.of(1L, 2L), document.get(NodeSerializers.LONG_LIST, Route.from("lists", "longs")));
        assertEquals(List.of(true, false), document.get(NodeSerializers.BOOLEAN_LIST, Route.from("lists", "booleans")));
        assertEquals(List.of(1, "two", true), document.get(NodeSerializers.SCALAR_LIST, Route.from("lists", "scalars")));
        assertEquals(3, document.get(NodeSerializers.OBJECT_LIST, Route.from("lists", "objects")).size());
    }

    private static YamlDocument document() throws IOException {
        return SparrowYaml.builder().build().load(YAML);
    }

    private enum TestMode {
        FIRST,
        SECOND
    }

    private record MapKey(String value) {
    }

    private record MapValue(int value) {
    }
}
