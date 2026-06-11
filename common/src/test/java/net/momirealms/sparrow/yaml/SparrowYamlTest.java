package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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

        @Test
        void should_CreateDefaultFileWithoutBackup_When_UpgradeFileTargetIsMissing() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            Path tempDir = Files.createTempDirectory("sparrow_missing_upgrade");
            Path configPath = tempDir.resolve("config.yml");
            YamlDocument defDocument = sparrowYaml.load("""
                    config-version: 1
                    value: default
                    """);

            YamlDocument upgraded = sparrowYaml.upgradeFile(
                    configPath.toFile(),
                    defDocument,
                    YamlUpgradePipeline.builder().build(),
                    true
            );

            assertSame(defDocument, upgraded);
            assertTrue(Files.exists(configPath));
            assertEquals("default", sparrowYaml.load(configPath).getNodeOrNull("value").value());
            try (var paths = Files.list(tempDir)) {
                assertEquals(1, paths.count(), "目标文件不存在时不应创建备份文件");
            }
        }

        @Test
        void should_CreateParentDirectories_When_UpgradeFileSavesDefaultDocument() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            Path tempDir = Files.createTempDirectory("sparrow_nested_upgrade");
            Path configPath = tempDir.resolve("nested").resolve("config.yml");
            YamlDocument defDocument = sparrowYaml.load("""
                    config-version: 1
                    value: default
                    """);

            YamlDocument upgraded = sparrowYaml.upgradeFile(
                    configPath.toFile(),
                    defDocument,
                    YamlUpgradePipeline.builder().build(),
                    false
            );

            assertSame(defDocument, upgraded);
            assertTrue(Files.exists(configPath));
            assertEquals("default", sparrowYaml.load(configPath).getNodeOrNull("value").value());
        }

        @Test
        void should_CreateBackup_When_UpgradeFileUsesExplicitBackupPath() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            Path tempDir = Files.createTempDirectory("sparrow_backup_upgrade");
            Path configPath = tempDir.resolve("config.yml");
            Path backupPath = tempDir.resolve("backup").resolve("config.yml.bak");
            Files.writeString(configPath, """
                    config-version: 1
                    old: keep
                    """);
            YamlDocument defDocument = sparrowYaml.load("""
                    config-version: 2
                    value: default
                    """);

            YamlDocument upgraded = sparrowYaml.upgradeFile(
                    configPath.toFile(),
                    defDocument,
                    YamlUpgradePipeline.builder().build(),
                    backupPath
            );

            assertTrue(Files.exists(backupPath));
            assertTrue(Files.readString(backupPath).contains("old: keep"));
            assertEquals("2", upgraded.getNodeOrNull("config-version").value().toString());
            assertEquals("default", upgraded.getNodeOrNull("value").value());
            assertNull(upgraded.getNodeOrNull("old"));
        }

        @Test
        void should_RejectBackupPath_When_ItMatchesLocalFilePath() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            Path configPath = Files.createTempFile("sparrow_same_backup", ".yml");
            YamlDocument defDocument = sparrowYaml.load("config-version: 1\n");
            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder().build();

            assertThrows(IllegalArgumentException.class, () -> sparrowYaml.upgradeFile(
                    configPath.toFile(),
                    defDocument,
                    pipeline,
                    configPath
            ));
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

        record NamedValue(String value) {}

        enum Mode {
            EASY,
            HARD
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
            NodeSerializer<List<String>> stringListCodec = NodeSerializers.STRING.listOf();
            NodeSerializer<java.util.Map<String, List<String>>> mapOfListCodec = stringListCodec.mapOf();
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
        void should_RoundTripMappingObject_When_UsingMappingBuilder() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Block> blockCodec = NodeSerializers.mapping(Block.class)
                    .group(
                            NodeSerializers.INT.fieldOf("x").forGetter(Block::x),
                            NodeSerializers.INT.fieldOf("y").forGetter(Block::y),
                            NodeSerializers.INT.fieldOf("z").forGetter(Block::z)
                    )
                    .apply(Block::new);
            sparrowYaml.serializers().register(Block.class, blockCodec);

            YamlDocument yamlDocument = sparrowYaml.load("""
            data:
              block:
                x: 1
                y: 2
                z: 3
            """);

            Block decoded = yamlDocument.get(Block.class, "data", "block");
            assertEquals(new Block(1, 2, 3), decoded);

            yamlDocument.setAndGet(Block.class, new Block(4, 5, 6), "data", "other");
            assertEquals(4, yamlDocument.get(NodeSerializers.INT, "data", "other", "x"));
            assertEquals(5, yamlDocument.get(NodeSerializers.INT, "data", "other", "y"));
            assertEquals(6, yamlDocument.get(NodeSerializers.INT, "data", "other", "z"));
        }

        @Test
        void should_RoundTripSequenceObject_When_UsingSequenceBuilder() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Block> blockCodec = NodeSerializers.sequence(Block.class)
                    .group(
                            NodeSerializers.INT.element(0).forGetter(Block::x),
                            NodeSerializers.INT.element(1).forGetter(Block::y),
                            NodeSerializers.INT.element(2).forGetter(Block::z)
                    )
                    .apply(Block::new);
            sparrowYaml.serializers().register(Block.class, blockCodec);

            YamlDocument yamlDocument = sparrowYaml.load("""
            data:
              block: [1, 2, 3]
            """);

            Block decoded = yamlDocument.get(Block.class, "data", "block");
            assertEquals(new Block(1, 2, 3), decoded);

            yamlDocument.setAndGet(Block.class, new Block(4, 5, 6), "data", "other");
            assertEquals(List.of(4, 5, 6), yamlDocument.get(NodeSerializers.INT.listOf(), "data", "other"));
        }

        @Test
        void should_ThrowMissingException_When_RequiredBuilderFieldIsMissing() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Block> blockCodec = NodeSerializers.mapping(Block.class)
                    .group(
                            NodeSerializers.INT.fieldOf("x").forGetter(Block::x),
                            NodeSerializers.INT.fieldOf("y").forGetter(Block::y),
                            NodeSerializers.INT.fieldOf("z").forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            block:
              x: 1
              z: 3
            """);

            MissingNodeException missing = assertThrows(MissingNodeException.class, () -> yamlDocument.get(blockCodec, "block"));
            assertEquals("y", missing.key());
            assertEquals(Route.from("block", "y"), missing.path());
            assertEquals(Integer.class, missing.targetType());
            assertEquals("Missing YAML value 'y' at path \"block.y\", expected Integer", missing.getMessage());
        }

        @Test
        void should_UseDefaultOnlyWhenBuilderFieldIsMissing_AndThrowWhenInvalid() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Block> blockCodec = NodeSerializers.mapping(Block.class)
                    .group(
                            NodeSerializers.INT.fieldOf("x").forGetter(Block::x),
                            NodeSerializers.INT.fieldOf("y").defaulted(64).forGetter(Block::y),
                            NodeSerializers.INT.fieldOf("z").forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument missing = sparrowYaml.load("""
            block:
              x: 1
              z: 3
            """);
            assertEquals(new Block(1, 64, 3), missing.get(blockCodec, "block"));

            YamlDocument invalid = sparrowYaml.load("""
            block:
              x: 1
              y:
                nested: true
              z: 3
            """);
            InvalidNodeException failure = assertThrows(InvalidNodeException.class, () -> invalid.get(blockCodec, "block"));
            assertEquals(Route.from("block", "y"), failure.path());
            assertEquals(SectionNode.class, failure.actualType());
            assertEquals(Integer.class, failure.targetType());
            assertEquals("Invalid YAML value at path \"block.y\": actual Map, expected Integer", failure.getMessage());
        }

        @Test
        void should_ThrowMissingException_When_RequiredBuilderElementIsMissing() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Block> blockCodec = NodeSerializers.sequence(Block.class)
                    .group(
                            NodeSerializers.INT.element(0).forGetter(Block::x),
                            NodeSerializers.INT.element(1).forGetter(Block::y),
                            NodeSerializers.INT.element(2).forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            block:
              - 1
              - 2
            """);

            MissingNodeException missing = assertThrows(MissingNodeException.class, () -> yamlDocument.get(blockCodec, "block"));
            assertEquals(2, missing.key());
            assertEquals(Route.from("block", 2), missing.path());
            assertEquals(Integer.class, missing.targetType());
            assertEquals("Missing YAML value '2' at path \"block[2]\", expected Integer", missing.getMessage());
        }

        @Test
        void should_ThrowInvalidException_When_BuilderRootNodeHasWrongShape() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Block> mappingCodec = NodeSerializers.mapping(Block.class)
                    .group(
                            NodeSerializers.INT.fieldOf("x").forGetter(Block::x),
                            NodeSerializers.INT.fieldOf("y").forGetter(Block::y),
                            NodeSerializers.INT.fieldOf("z").forGetter(Block::z)
                    )
                    .apply(Block::new);
            NodeSerializer<Block> sequenceCodec = NodeSerializers.sequence(Block.class)
                    .group(
                            NodeSerializers.INT.element(0).forGetter(Block::x),
                            NodeSerializers.INT.element(1).forGetter(Block::y),
                            NodeSerializers.INT.element(2).forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            sequence: [1, 2, 3]
            mapping:
              x: 1
              y: 2
              z: 3
            """);

            InvalidNodeException mappingFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(mappingCodec, "sequence")
            );
            assertEquals(Route.from("sequence"), mappingFailure.path());
            assertEquals(SequenceNode.class, mappingFailure.actualType());
            assertEquals(Block.class, mappingFailure.targetType());
            assertEquals("Invalid YAML value at path \"sequence\": actual List, expected Block", mappingFailure.getMessage());

            InvalidNodeException sequenceFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(sequenceCodec, "mapping")
            );
            assertEquals(Route.from("mapping"), sequenceFailure.path());
            assertEquals(SectionNode.class, sequenceFailure.actualType());
            assertEquals(Block.class, sequenceFailure.targetType());
            assertEquals("Invalid YAML value at path \"mapping\": actual Map, expected Block", sequenceFailure.getMessage());
        }

        @Test
        void should_UseFallback_When_BuilderFieldIsMissingOrInvalid() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            List<RuntimeException> failures = new ArrayList<>();
            NodeSerializer<Block> blockCodec = NodeSerializers.mapping(Block.class)
                    .group(
                            NodeSerializers.INT.fieldOf("x").onFail(failure -> {
                                failures.add(failure);
                                return 10;
                            }).forGetter(Block::x),
                            NodeSerializers.INT.fieldOf("y").onFail(failure -> {
                                failures.add(failure);
                                return 20;
                            }).forGetter(Block::y),
                            NodeSerializers.INT.fieldOf("z").forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            block:
              x:
                bad: true
              z: 3
            """);

            assertEquals(new Block(10, 20, 3), yamlDocument.get(blockCodec, "block"));
            InvalidNodeException invalid = assertInstanceOf(InvalidNodeException.class, failures.get(0));
            assertEquals(Route.from("block", "x"), invalid.path());
            assertEquals(SectionNode.class, invalid.actualType());
            assertEquals(Integer.class, invalid.targetType());
            MissingNodeException missing = assertInstanceOf(MissingNodeException.class, failures.get(1));
            assertEquals("y", missing.key());
            assertEquals(Route.from("block", "y"), missing.path());
            assertEquals(Integer.class, missing.targetType());
        }

        @Test
        void should_UseFallback_When_BuilderElementIsMissingOrInvalid() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            List<RuntimeException> failures = new ArrayList<>();
            NodeSerializer<Block> blockCodec = NodeSerializers.sequence(Block.class)
                    .group(
                            NodeSerializers.INT.element(0).onFail(failure -> {
                                failures.add(failure);
                                return 10;
                            }).forGetter(Block::x),
                            NodeSerializers.INT.element(1).forGetter(Block::y),
                            NodeSerializers.INT.element(2).onFail(failure -> {
                                failures.add(failure);
                                return 30;
                            }).forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            block:
              - {bad: true}
              - 20
            """);

            assertEquals(new Block(10, 20, 30), yamlDocument.get(blockCodec, "block"));
            InvalidNodeException invalid = assertInstanceOf(InvalidNodeException.class, failures.get(0));
            assertEquals(Route.from("block", 0), invalid.path());
            assertEquals(SectionNode.class, invalid.actualType());
            assertEquals(Integer.class, invalid.targetType());
            MissingNodeException missing = assertInstanceOf(MissingNodeException.class, failures.get(1));
            assertEquals(2, missing.key());
            assertEquals(Route.from("block", 2), missing.path());
            assertEquals(Integer.class, missing.targetType());
        }

        @Test
        void should_PassSerializerExceptionToFallback_When_DelegateThrows() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            List<RuntimeException> failures = new ArrayList<>();
            NodeSerializer<Integer> throwingInt = NodeSerializers.STRING.xmap(
                    value -> {
                        throw new InvalidNodeException(Route.from("inner"), String.class, Integer.class);
                    },
                    String::valueOf
            );
            NodeSerializer<Block> blockCodec = NodeSerializers.mapping(Block.class)
                    .group(
                            throwingInt.fieldOf("x").onFail(failure -> {
                                failures.add(failure);
                                return 10;
                            }).forGetter(Block::x),
                            NodeSerializers.INT.fieldOf("y").forGetter(Block::y),
                            NodeSerializers.INT.fieldOf("z").forGetter(Block::z)
                    )
                    .apply(Block::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            block:
              x: 1
              y: 2
              z: 3
            """);

            assertEquals(new Block(10, 2, 3), yamlDocument.get(blockCodec, "block"));
            assertEquals(1, failures.size());
            InvalidNodeException invalid = assertInstanceOf(InvalidNodeException.class, failures.get(0));
            assertEquals(Route.from("inner"), invalid.path());
            assertEquals(String.class, invalid.actualType());
            assertEquals(Integer.class, invalid.targetType());
        }

        @Test
        void should_DecodeSetInEncounterOrder_When_UsingSetOf() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            tags:
              - alpha
              - beta
              - alpha
            """);

            Set<String> tags = yamlDocument.get(NodeSerializers.STRING.setOf(), "tags");

            assertEquals(List.of("alpha", "beta"), new ArrayList<>(tags));
        }

        @Test
        void should_ThrowInvalidException_When_CollectionCombinatorRootShapeIsWrong() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            scalar: value
            mapping:
              key: value
            sequence:
              - value
            """);

            InvalidNodeException listFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.STRING.listOf(), "mapping")
            );
            assertEquals(Route.from("mapping"), listFailure.path());
            assertEquals(SectionNode.class, listFailure.actualType());
            assertEquals(List.class, listFailure.targetType());

            InvalidNodeException setFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.STRING.setOf(), "scalar")
            );
            assertEquals(Route.from("scalar"), setFailure.path());
            assertEquals(String.class, setFailure.actualType());
            assertEquals(Set.class, setFailure.targetType());

            InvalidNodeException mapFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.STRING.mapOf(), "sequence")
            );
            assertEquals(Route.from("sequence"), mapFailure.path());
            assertEquals(SequenceNode.class, mapFailure.actualType());
            assertEquals(Map.class, mapFailure.targetType());
        }

        @Test
        void should_ThrowInvalidException_When_XmapMapperFailsOrReturnsNull() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            value: cat
            """);

            NodeSerializer<NamedValue> nullingCodec = NodeSerializers.STRING.xmap(
                    value -> null,
                    NamedValue::value
            );
            InvalidNodeException nullFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(nullingCodec, "value")
            );
            assertEquals(Route.from("value"), nullFailure.path());
            assertEquals(String.class, nullFailure.actualType());
            assertEquals(Object.class, nullFailure.targetType());

            NodeSerializer<NamedValue> throwingCodec = NodeSerializers.STRING.xmap(
                    value -> {
                        throw new IllegalArgumentException("bad value");
                    },
                    NamedValue::value
            );
            InvalidNodeException throwingFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(throwingCodec, "value")
            );
            assertEquals(Route.from("value"), throwingFailure.path());
            assertEquals(String.class, throwingFailure.actualType());
            assertEquals(Object.class, throwingFailure.targetType());
            assertInstanceOf(IllegalArgumentException.class, throwingFailure.getCause());
        }

        @Test
        void should_DecodeScalarListAndMap_When_UsingObjectSerializer() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            scalar: value
            sequence:
              - alpha
              - 2
            mapping:
              name: cat
              nested:
                enabled: true
            """);

            assertEquals("value", yamlDocument.get(NodeSerializers.OBJECT, "scalar"));
            assertEquals(List.of("alpha", 2), yamlDocument.get(NodeSerializers.OBJECT, "sequence"));

            @SuppressWarnings("unchecked")
            Map<Object, Object> mapping = (Map<Object, Object>) yamlDocument.get(NodeSerializers.OBJECT, "mapping");
            assertEquals("cat", mapping.get("name"));
            assertEquals(Map.of("enabled", true), mapping.get("nested"));
        }

        @Test
        void should_RejectCollectionNodes_When_UsingScalarSerializer() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            mapping:
              key: value
            sequence:
              - value
            """);

            InvalidNodeException mappingFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.SCALAR, "mapping")
            );
            assertEquals(Route.from("mapping"), mappingFailure.path());
            assertEquals(SectionNode.class, mappingFailure.actualType());
            assertEquals(Object.class, mappingFailure.targetType());

            InvalidNodeException sequenceFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.SCALAR, "sequence")
            );
            assertEquals(Route.from("sequence"), sequenceFailure.path());
            assertEquals(SequenceNode.class, sequenceFailure.actualType());
            assertEquals(Object.class, sequenceFailure.targetType());
        }

        @Test
        void should_ThrowMissingException_When_GetPathIsMissing_AndUseDefaultOnlyForMissingPath() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            value: 5
            """);

            MissingNodeException missing = assertThrows(
                    MissingNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.INT, "missing")
            );
            assertEquals("missing", missing.key());
            assertEquals(Route.from("missing"), missing.path());
            assertEquals(Integer.class, missing.targetType());

            MissingNodeException classMissing = assertThrows(
                    MissingNodeException.class,
                    () -> yamlDocument.get(Integer.class, "missing-class")
            );
            assertEquals("missing-class", classMissing.key());
            assertEquals(Route.from("missing-class"), classMissing.path());
            assertEquals(Integer.class, classMissing.targetType());

            TypeRef<List<String>> listType = new TypeRef<>() {};
            MissingNodeException typeRefMissing = assertThrows(
                    MissingNodeException.class,
                    () -> yamlDocument.get(listType, "missing-list")
            );
            assertEquals("missing-list", typeRefMissing.key());
            assertEquals(Route.from("missing-list"), typeRefMissing.path());
            assertEquals(List.class, typeRefMissing.targetType());

            assertEquals(42, yamlDocument.getOrDefault(NodeSerializers.INT, 42, "missing"));
            assertEquals(5, yamlDocument.getOrDefault(NodeSerializers.INT, 42, "value"));
        }

        @Test
        void should_KeepInvalidException_When_GetOrDefaultReadsInvalidExistingValue() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            value: nope
            """);

            InvalidNodeException failure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.getOrDefault(NodeSerializers.INT, 42, "value")
            );
            assertEquals(Route.from("value"), failure.path());
            assertEquals(String.class, failure.actualType());
            assertEquals(Integer.class, failure.targetType());
        }

        @Test
        @SuppressWarnings("unchecked")
        void should_DecodeRecursiveStructure_When_UsingNodeSerializersLazy() throws IOException {
            record Tree(String name, List<Tree> children) {}

            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Tree>[] ref = new NodeSerializer[1];
            ref[0] = NodeSerializers.mapping(Tree.class)
                    .group(
                            NodeSerializers.STRING.fieldOf("name").forGetter(Tree::name),
                            NodeSerializers.lazy(() -> ref[0]).listOf().fieldOf("children").defaulted(List.of()).forGetter(Tree::children)
                    )
                    .apply(Tree::new);

            YamlDocument yamlDocument = sparrowYaml.load("""
            root:
              name: parent
              children:
                - name: child
            """);

            Tree root = yamlDocument.get(ref[0], "root");

            assertEquals("parent", root.name());
            assertEquals(List.of(new Tree("child", List.of())), root.children());
        }

        @Test
        void should_DecodeScalarValueObject_When_UsingStringBacked() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<NamedValue> namedValueCodec = NodeSerializers.stringBacked(
                    value -> value.startsWith("name:") ? new NamedValue(value.substring(5)) : null,
                    value -> "name:" + value.value()
            );
            YamlDocument yamlDocument = sparrowYaml.load("""
            value: "name:cat"
            invalid: "cat"
            """);

            assertEquals(new NamedValue("cat"), yamlDocument.get(namedValueCodec, "value"));
            InvalidNodeException invalid = assertThrows(InvalidNodeException.class, () -> yamlDocument.get(namedValueCodec, "invalid"));
            assertEquals(Route.from("invalid"), invalid.path());
            assertEquals(String.class, invalid.actualType());
            assertEquals(Object.class, invalid.targetType());

            yamlDocument.setAndGet(namedValueCodec, new NamedValue("dog"), "encoded");
            assertEquals("name:dog", yamlDocument.get(NodeSerializers.STRING, "encoded"));
        }

        @Test
        void should_ThrowInvalidException_When_BuiltInSerializerCannotParseValue() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            YamlDocument yamlDocument = sparrowYaml.load("""
            number: nope
            bool: maybe
            uuid: definitely-not-a-uuid
            section:
              nested: true
            """);

            InvalidNodeException numberFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.INT, "number")
            );
            assertEquals(Route.from("number"), numberFailure.path());
            assertEquals(String.class, numberFailure.actualType());
            assertEquals(Integer.class, numberFailure.targetType());

            InvalidNodeException typeFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.STRING, "section")
            );
            assertEquals(Route.from("section"), typeFailure.path());
            assertEquals(SectionNode.class, typeFailure.actualType());
            assertEquals(String.class, typeFailure.targetType());

            InvalidNodeException boolFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.BOOLEAN, "bool")
            );
            assertEquals(Route.from("bool"), boolFailure.path());
            assertEquals(String.class, boolFailure.actualType());
            assertEquals(Boolean.class, boolFailure.targetType());

            InvalidNodeException uuidFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(NodeSerializers.UUID, "uuid")
            );
            assertEquals(Route.from("uuid"), uuidFailure.path());
            assertEquals(String.class, uuidFailure.actualType());
            assertEquals(UUID.class, uuidFailure.targetType());
        }

        @Test
        void should_ThrowInvalidException_When_EnumSerializerCannotParseValue() throws IOException {
            SparrowYaml sparrowYaml = SparrowYaml.builder().build();
            NodeSerializer<Mode> modeCodec = NodeSerializers.enumCodec(Mode.class);
            YamlDocument yamlDocument = sparrowYaml.load("""
            mode: medium
            section:
              nested: true
            """);

            InvalidNodeException valueFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(modeCodec, "mode")
            );
            assertEquals(Route.from("mode"), valueFailure.path());
            assertEquals(String.class, valueFailure.actualType());
            assertEquals(Mode.class, valueFailure.targetType());
            assertEquals("Invalid YAML value at path \"mode\": actual String, expected Mode", valueFailure.getMessage());

            InvalidNodeException typeFailure = assertThrows(
                    InvalidNodeException.class,
                    () -> yamlDocument.get(modeCodec, "section")
            );
            assertEquals(Route.from("section"), typeFailure.path());
            assertEquals(SectionNode.class, typeFailure.actualType());
            assertEquals(Mode.class, typeFailure.targetType());
            assertEquals("Invalid YAML value at path \"section\": actual Map, expected Mode", typeFailure.getMessage());
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
