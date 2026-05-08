package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeDecoder;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SparrowYaml 核心功能综合测试.
 * 测试用例按功能划分为: 文档 IO、节点读取、节点写入与同步、注释与路由、编解码器组合.
 */
class SparrowYamlTest {

    @Test
    @SuppressWarnings("deprecation")
    void should_SerializeAndDeserialize_TimeAndUUID_Properly() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument doc = yaml.load("test: test");
        
        UUID uuid = UUID.randomUUID();
        Date date = new Date();
        Instant instant = Instant.now();
        LocalDate localDate = LocalDate.now();
        LocalTime localTime = LocalTime.now();
        LocalDateTime localDateTime = LocalDateTime.now();
        Duration duration = Duration.ofDays(1);
        Period period = Period.ofDays(2);
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        Locale localeLanguage = Locale.ENGLISH;
        Locale localeCountry = Locale.US;
        Locale localeVariant = new Locale("zh", "CN", "PINYIN");
        
        doc.setAndGet(UUID.class, uuid, "uuid");
        doc.setAndGet(Date.class, date, "date");
        doc.setAndGet(Instant.class, instant, "instant");
        doc.setAndGet(LocalDate.class, localDate, "local_date");
        doc.setAndGet(LocalTime.class, localTime, "local_time");
        doc.setAndGet(LocalDateTime.class, localDateTime, "local_date_time");
        doc.setAndGet(Duration.class, duration, "duration");
        doc.setAndGet(Period.class, period, "period");
        doc.setAndGet(ZonedDateTime.class, zonedDateTime, "zoned_date_time");
        doc.setAndGet(Locale.class, localeLanguage, "locale_language");
        doc.setAndGet(Locale.class, localeCountry, "locale_country");
        doc.setAndGet(Locale.class, localeVariant, "locale_variant");
        doc.setAndGet(UUID.class, null, "empty_uuid");
        doc.setAndGet(Locale.class, null, "empty_locale");
        doc.setAndGet(LocalDate.class, null, "empty_local_date");
        
        java.io.StringWriter writer = new java.io.StringWriter();
        doc.save(writer);
        String dumped = writer.toString();
        assertTrue(dumped.contains("empty_uuid: ''"));
        assertTrue(dumped.contains("empty_locale: ''"));
        assertTrue(dumped.contains("empty_local_date: ''"));
        
        YamlDocument loadedDoc = yaml.load(dumped);
        
        assertEquals(uuid, loadedDoc.get(java.util.UUID.class, "uuid"));
        assertEquals(date, loadedDoc.get(java.util.Date.class, "date"));
        assertEquals(instant, loadedDoc.get(java.time.Instant.class, "instant"));
        assertEquals(localDate, loadedDoc.get(java.time.LocalDate.class, "local_date"));
        assertEquals(localTime, loadedDoc.get(java.time.LocalTime.class, "local_time"));
        assertEquals(localDateTime, loadedDoc.get(java.time.LocalDateTime.class, "local_date_time"));
        assertEquals(duration, loadedDoc.get(java.time.Duration.class, "duration"));
        assertEquals(period, loadedDoc.get(java.time.Period.class, "period"));
        assertEquals(zonedDateTime, loadedDoc.get(java.time.ZonedDateTime.class, "zoned_date_time"));
        assertEquals(localeLanguage, loadedDoc.get(java.util.Locale.class, "locale_language"));
        assertEquals(localeCountry, loadedDoc.get(java.util.Locale.class, "locale_country"));
        assertEquals(localeVariant, loadedDoc.get(java.util.Locale.class, "locale_variant"));
        assertNull(loadedDoc.get(java.util.UUID.class, "empty_uuid"));
        assertNull(loadedDoc.get(java.util.Locale.class, "empty_locale"));
        assertNull(loadedDoc.get(java.time.LocalDate.class, "empty_local_date"));
    }

    @Nested
    class DocumentIoTests {

        @Test
        void should_LoadYamlDocument_When_ReadingFromResources() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();

            // 2. 执行阶段 (Act)
            InputStream resource = SparrowYamlTest.class.getClassLoader().getResourceAsStream("test.yml");
            YamlDocument fromInputStream = sparrowYaml.load(resource);
            YamlDocument fromResource = sparrowYaml.loadFromResource("test.yml");

            // 3. 断言阶段 (Assert)
            assertNotNull(fromInputStream, "从 InputStream 加载文档不应为空");
            assertNotNull(fromResource, "从 Resource 快捷加载文档不应为空");
        }

        @Test
        void should_ReturnTrue_When_DocumentIsEmpty() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            
            // 2. 执行阶段 (Act)
            YamlDocument yamlDocument = sparrowYaml.load("");
            
            // 3. 断言阶段 (Assert)
            assertTrue(yamlDocument.isEmptyDocument(), "加载空字符串应返回空文档状态");
        }
    }

    @Nested
    class NodeReadTests {

        @Test
        void should_ReadScalarNode_When_NodeIsSimpleValue() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("single-key: \"success!\"");

            // 2. 执行阶段 (Act)
            YamlNode<?> yamlNode = yamlDocument.getNodeOrNull("single-key");
            ScalarNode scalarNode = yamlDocument.getScalarOrNull("single-key");

            // 3. 断言阶段 (Assert)
            assertNotNull(yamlNode);
            assertInstanceOf(ScalarNode.class, yamlNode);
            assertEquals(yamlNode, scalarNode, "按通用节点和 Scalar 节点获取的结果应相同");
            assertEquals("success!", scalarNode.value());
        }

        @Test
        void should_ReadMappingNode_When_NodeIsSection() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            mapping-key:
                user: "Catnies"
                id: 114514
            """);

            // 2. 执行阶段 (Act)
            SectionNode sectionNode = yamlDocument.getSectionOrNull("mapping-key");
            ScalarNode userNode = yamlDocument.getScalarOrNull("mapping-key", "user");
            ScalarNode idNode = yamlDocument.getScalarOrNull("mapping-key", "id");

            // 3. 断言阶段 (Assert)
            assertNotNull(sectionNode);
            assertEquals("Catnies", userNode.value());
            assertEquals(114514, idNode.value());
        }

        @Test
        void should_ReadSequenceNode_When_NodeIsList() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            list-key:
                - "string"
                - 888
                - map: "value"
                - - "nested"
                  - 100.0
            """);

            // 2. 执行阶段 (Act)
            SequenceNode sequenceNode = yamlDocument.getSequenceOrNull("list-key");
            SequenceNode nestedSequenceNode = yamlDocument.getSequenceOrNull("list-key", 3);

            // 3. 断言阶段 (Assert)
            assertNotNull(sequenceNode);
            assertEquals("string", sequenceNode.getScalarOrNull(0).value());
            assertEquals(888, sequenceNode.getScalarOrNull(1).value());
            assertEquals("value", sequenceNode.getSectionOrNull(2).getScalarOrNull("map").value());
            
            assertNotNull(nestedSequenceNode);
            assertEquals("nested", nestedSequenceNode.getScalarOrNull(0).value());
            assertEquals(100.0, nestedSequenceNode.getScalarOrNull(1).value());
        }

        @Test
        void should_ReturnTypedNode_When_UsingGetOrThrow() throws Throwable {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            scalar: value
            section:
              key: value
            sequence:
              - item
            """);

            // 2. 执行阶段 (Act) & 3. 断言阶段 (Assert)
            assertEquals("value", yamlDocument.getScalarOrThrow(null, "scalar").value());
            assertSame(yamlDocument.getSectionOrNull("section"), yamlDocument.getSectionOrThrow(null, "section"));
            assertSame(yamlDocument.getSequenceOrNull("sequence"), yamlDocument.getSequenceOrThrow(null, "sequence"));
            assertThrows(NoSuchElementException.class, () -> yamlDocument.getScalarOrThrow(null, "missing"));
            assertThrows(NoSuchElementException.class, () -> yamlDocument.getScalarOrThrow(null, "section"));
        }
    }

    @Nested
    class RouteTests {

        @Test
        void should_ConcatenateRoutes_When_AddingRoute() {
            // 1. 准备阶段 (Arrange)
            Route route = Route.from("root").add(Route.from("child", 1));

            // 2. 执行阶段 (Act) & 3. 断言阶段 (Assert)
            assertEquals(3, route.length());
            assertEquals("root", route.getRouteElement(0).key());
            assertEquals("child", route.getRouteElement(1).key());
            assertEquals(1, route.getRouteElement(2).key());
        }

        @Test
        void should_HashEmptyRoute_When_UsedInCollections() {
            // 1. 准备阶段 (Arrange)
            Set<Route> routes = new HashSet<>();

            // 2. 执行阶段 (Act) & 3. 断言阶段 (Assert)
            assertDoesNotThrow(() -> routes.add(Route.empty()));
            assertTrue(routes.contains(Route.empty()));
        }
    }

    @Nested
    class NodeWriteAndSyncTests {

        @Test
        void should_CreateSubNodes_When_SettingValuesInEmptyDocument() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("");

            // 2. 执行阶段 (Act) - 测试 Scalar
            yamlDocument.setSubNode("scalar", "value");
            
            // 2. 执行阶段 (Act) - 测试 Section (Map)
            yamlDocument.setSubNode("section", new LinkedHashMap<>() {{
                this.put("user", "Catnies");
                this.put("id", 114514);
            }});
            
            // 2. 执行阶段 (Act) - 测试 Sequence (List)
            yamlDocument.setSubNode("list", new ArrayList<>() {{
                this.add("hello");
                this.add(100);
            }});

            // 3. 断言阶段 (Assert)
            assertEquals("value", yamlDocument.getScalarOrNull("scalar").value());
            assertEquals("Catnies", yamlDocument.getScalarOrNull("section", "user").value());
            assertEquals(114514, yamlDocument.getScalarOrNull("section", "id").value());
            assertEquals("hello", yamlDocument.getScalarOrNull("list", 0).value());
            assertEquals(100, yamlDocument.getScalarOrNull("list", 1).value());
        }

        @Test
        void should_AutoFillWithNull_When_SettingSequenceNodeOutOfBound() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            sequence:
              - "first"
              - "second"
            """);
            SequenceNode sequenceNode = yamlDocument.getSequenceOrNull("sequence");

            // 2. 执行阶段 (Act)
            sequenceNode.setSubNode(0, "modified_first"); // 修改已有
            sequenceNode.setSubNode(4, "fifth");          // 越界追加

            // 3. 断言阶段 (Assert)
            assertEquals(5, sequenceNode.size(), "越界追加应导致列表扩容至目标大小");
            assertEquals("modified_first", sequenceNode.getScalarOrNull(0).value());
            assertNull(sequenceNode.getScalarOrNull(2).value(), "中间空缺的部分应使用 Null 补全");
            assertNull(sequenceNode.getScalarOrNull(3).value());
            assertEquals("fifth", sequenceNode.getScalarOrNull(4).value());
        }

        @Test
        void should_SyncChanges_When_DumpingDocument() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            # Root Comment
            mapping:
              key1: "value1"
            list:
              - "a"
            """);

            // 2. 执行阶段 (Act)
            yamlDocument.getScalarOrNull("mapping", "key1").setValue("modified_value");
            yamlDocument.getSectionOrNull("mapping").setSubNode("new_key", "new_value");
            yamlDocument.getSequenceOrNull("list").setSubNode(1, "b");

            StringWriter writer = new StringWriter();
            yamlDocument.save(writer);
            String dumped = writer.toString();

            // 3. 断言阶段 (Assert)
            assertTrue(dumped.contains("key1: modified_value"), "修改的节点值应被同步");
            assertTrue(dumped.contains("new_key: new_value"), "新增的节点应被同步");
            assertTrue(dumped.contains("- b"), "追加的列表项应被同步");
            assertTrue(dumped.contains("# Root Comment"), "原有的注释应被保留");
        }
    }

    @Nested
    class CommentAndRouteTests {

        @Test
        void should_MaintainComments_When_AddingCommentsProgrammatically() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("");
            yamlDocument.setSubNode("test_key", "test_value");
            ScalarNode scalarNode = yamlDocument.getScalarOrNull("test_key");

            // 2. 执行阶段 (Act)
            List<CommentLine> beforeComments = new ArrayList<>();
            beforeComments.add(new CommentLine(Optional.empty(), Optional.empty(), " Before comment", CommentType.BLOCK));
            scalarNode.setBeforeKeyComments(beforeComments);

            List<CommentLine> inlineComments = new ArrayList<>();
            inlineComments.add(new CommentLine(Optional.empty(), Optional.empty(), " Inline comment", CommentType.IN_LINE));
            scalarNode.setInlineValueComments(inlineComments);

            StringWriter writer = new StringWriter();
            yamlDocument.save(writer);
            String dumped = writer.toString();

            // 3. 断言阶段 (Assert)
            assertTrue(dumped.contains("test_key: test_value"));
            assertTrue(dumped.contains("# Inline comment"), "内联注释应被正确渲染");
        }

        @Test
        void should_UpdateRouteDynamically_When_NodeIsMoved() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("");

            // 2. 执行阶段 (Act) - 创建 A.B.C
            yamlDocument.set(Route.from("A", "B", "C"), "value");
            ScalarNode nodeC = yamlDocument.getScalarOrNull("A", "B", "C");
            assertEquals(Route.from("A", "B", "C"), nodeC.route(), "初始路由应匹配");

            // 将 B 节点移动到 D 下
            SectionNode nodeB = yamlDocument.getSectionOrNull("A", "B");
            yamlDocument.setSubNode("D", SectionNode.createEmpty(yamlDocument));
            SectionNode nodeD = yamlDocument.getSectionOrNull("D");
            
            nodeD.setSubNode("B", nodeB);

            // 3. 断言阶段 (Assert)
            assertEquals(Route.from("D", "B", "C"), nodeC.route(), "节点移动后，其路由(Route)应动态更新以反映新位置");
        }
        
        @Test
        void should_CopyCommentsAndStyle_When_UsingNodeUtilities() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            source: [1, 2]
            target:
              - 3
              - 4
            """);

            SequenceNode source = yamlDocument.getSequenceOrNull("source");
            SequenceNode target = yamlDocument.getSequenceOrNull("target");

            // 给源节点添加内联注释
            List<CommentLine> inlineComments = new ArrayList<>();
            inlineComments.add(new CommentLine(Optional.empty(), Optional.empty(), " copied", CommentType.IN_LINE));
            source.getScalarOrNull(0).setInlineValueComments(inlineComments);

            // 2. 执行阶段 (Act)
            source.deepCopyCommentsTo(target);
            source.preserveFlowStyleTo(target);

            // 3. 断言阶段 (Assert)
            assertEquals(" copied", target.getScalarOrNull(0).inlineValueComments().get(0).getValue(), "注释应被深拷贝至目标节点");
            
            StringWriter writer = new StringWriter();
            yamlDocument.save(writer);
            assertTrue(writer.toString().contains("target: [3, 4]"), "Flow style 应被同步，使得 target 也变为单行数组格式");
        }
    }

    @Nested
    class CodecCombinatorTests {

        record Block(int x, int y, int z) {
            public List<Integer> toList() {
                return List.of(x, y, z);
            }
        }

        @Test
        void should_DecodeMapOfList_When_UsingCombinatorCodec() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            users:
              user1:
                - "read"
                - "write"
              user2:
                - "read"
            """);

            // 2. 执行阶段 (Act)
            NodeDecoder<List<String>> stringListCodec = NodeSerializers.STRING.listOf();
            NodeDecoder<java.util.Map<String, List<String>>> mapOfListCodec = stringListCodec.mapOf();
            java.util.Map<String, List<String>> users = yamlDocument.get(mapOfListCodec, "users");

            // 3. 断言阶段 (Assert)
            assertNotNull(users);
            assertEquals(2, users.size());
            assertEquals(List.of("read", "write"), users.get("user1"));
            assertEquals(List.of("read"), users.get("user2"));
        }

        @Test
        void should_DecodeCustomObject_When_UsingXmapCombinator() throws IOException {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            data:
              block: [1, 2, 3]
            """);

            // 2. 执行阶段 (Act)
            NodeSerializer<Block> blockCodec = NodeSerializers.INT.listOf().xmap(
                    it -> new Block(it.get(0), it.get(1), it.get(2)),
                    Block::toList
            );
            Block block = yamlDocument.get(blockCodec, "data", "block");

            // 3. 断言阶段 (Assert)
            assertNotNull(block);
            assertEquals(1, block.x());
            assertEquals(2, block.y());
            assertEquals(3, block.z());
        }

        @Test
        void should_DecodeBasicTypes_When_UsingBuiltInCodecs() throws Throwable {
            // 1. 准备阶段 (Arrange)
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.loadFromResource("full-test.yml");

            // 2. 执行阶段 (Act) & 3. 断言阶段 (Assert)
            Integer integer1 = yamlDocument.get(NodeSerializers.INT, "test", "int");
            assertEquals(1314, integer1);

            String string1 = yamlDocument.get(NodeSerializers.STRING, "test", "string-1");
            assertEquals("test", string1);

            String quoted = yamlDocument.get(NodeSerializers.STRING, "test", "quoted");
            assertEquals("So does this quoted scalar.\n", quoted);

            List<String> stringList = yamlDocument.get(NodeSerializers.STRING.listOf(), "test", "string_list");
            assertEquals(List.of("qwq", "awa", "fwf"), stringList);
        }
    }
}
