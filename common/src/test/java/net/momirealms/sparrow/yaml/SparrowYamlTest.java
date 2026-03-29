package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SparrowYamlTest {
    public static void main(String[] args) throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        InputStream resource = SnakeYamlComposeTest.class.getClassLoader().getResourceAsStream("test.yml");
        YamlDocument yamlDocument = sparrowYaml.load(resource);

        ScalarNode scalarNode1 = yamlDocument.getScalarOrNull("first-key", "2");
        ScalarNode scalarNode2 = yamlDocument.getScalarOrNull("first-key", "2.0");

        System.out.println("Finish!");
    }

    @Test
    void loadYamlFile() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();

        // InputStream
        InputStream resource = SnakeYamlComposeTest.class.getClassLoader().getResourceAsStream("test.yml");
        YamlDocument inputStream = sparrowYaml.load(resource);
        Assertions.assertNotNull(inputStream);

        // Resource
        YamlDocument fromResource = sparrowYaml.loadFromResource("test.yml");
        Assertions.assertNotNull(fromResource);
    }

    @Test
    void readScalarNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("""
        single-key: "success!"
        """);

        YamlNode<?> yamlNode = yamlDocument.getNodeOrNull("single-key");
        ScalarNode scalarNode = yamlDocument.getScalarOrNull("single-key");

        Assertions.assertNotNull(yamlNode);
        Assertions.assertNotNull(scalarNode);
        Assertions.assertInstanceOf(ScalarNode.class, yamlNode);
        Assertions.assertEquals(yamlNode, scalarNode);
        Assertions.assertEquals("success!", scalarNode.value());
    }

    @Test
    void readMappingNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("""
        mapping-key:
            user: "Catnies"
            id: 114514
        """);

        YamlNode<?> yamlNode = yamlDocument.getNodeOrNull("mapping-key");
        SectionNode sectionNode = yamlDocument.getSectionOrNull("mapping-key");
        ScalarNode userSectionNode = yamlDocument.getScalarOrNull("mapping-key", "user");
        ScalarNode idSectionNode = yamlDocument.getScalarOrNull("mapping-key", "id");

        Assertions.assertNotNull(yamlNode);
        Assertions.assertNotNull(sectionNode);
        Assertions.assertInstanceOf(SectionNode.class, sectionNode);
        Assertions.assertEquals(yamlNode, sectionNode);
        Assertions.assertEquals("Catnies", userSectionNode.value());
        Assertions.assertEquals(114514, idSectionNode.value());
    }

    @Test
    void readSequenceNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("""
        list-key:
            - "string"
            - 888
            - map: "value"
            - - "nested"
              - 100.0
        """);

        YamlNode<?> yamlNode = yamlDocument.getNodeOrNull("list-key");
        SequenceNode sequenceNode = yamlDocument.getSequenceOrNull("list-key");
        SequenceNode nestedSequenceNode = yamlDocument.getSequenceOrNull("list-key", 3);

        Object string = sequenceNode.getScalarOrNull(0).value();
        Object intNumber = sequenceNode.getScalarOrNull(1).value();
        SectionNode sectionNode = sequenceNode.getSectionOrNull(2);
        Object value = sectionNode.getScalarOrNull("map").value();
        Object nested = nestedSequenceNode.getScalarOrNull(0).value();
        Object doubleNumber = nestedSequenceNode.getScalarOrNull(1).value();

        Assertions.assertNotNull(yamlNode);
        Assertions.assertNotNull(sequenceNode);
        Assertions.assertNotNull(nestedSequenceNode);
        Assertions.assertEquals("string", string);
        Assertions.assertEquals(888, intNumber);
        Assertions.assertEquals("value", value);
        Assertions.assertEquals("nested", nested);
        Assertions.assertEquals(100.0, doubleNumber);
    }

    @Test
    void isEmptyDocument() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("");
        Assertions.assertTrue(yamlDocument.isEmptyDocument());
    }

    @Test
    void createScalarSubNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("");

        yamlDocument.setSubNode("scalar", "value");
        Object value = yamlDocument.getScalarOrNull("scalar").value();
        Assertions.assertEquals("value", value);
    }

    @Test
    void createSectionSubNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("");

        yamlDocument.setSubNode("section", new LinkedHashMap<>() {{
            this.put("user", "Catnies");
            this.put("id", 114514);
        }});
        Object user = yamlDocument.getScalarOrNull("section", "user").value();
        Object id = yamlDocument.getScalarOrNull("section", "id").value();
        Assertions.assertEquals("Catnies", user);
        Assertions.assertEquals(114514, id);
    }

    @Test
    void createSequenceSubNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("");

        yamlDocument.setSubNode("list", new ArrayList<>() {{
            this.add("こんにちは！");
            this.add(new ArrayList<>() {{
                this.add(100);
                this.add(new LinkedHashMap<>() {{
                    this.put("key", "value");
                }});
            }});
        }});
        Object japanese = yamlDocument.getScalarOrNull("list", 0).value();
        Object intNumber = yamlDocument.getScalarOrNull("list", 1, 0).value();
        Object value = yamlDocument.getScalarOrNull("list", 1, 1, "key").value();
        Assertions.assertEquals("こんにちは！", japanese);
        Assertions.assertEquals(100, intNumber);
        Assertions.assertEquals("value", value);
    }

    @Test
    void createNode() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.load("");

        YamlNode<?> createdNode = yamlDocument.setNode(Route.from("mapping"), new LinkedHashMap<>() {{
            this.put("single", "single-value");
            this.put("list", new ArrayList<>() {{
                this.add(100);
                this.add(new LinkedHashMap<>() {{
                    this.put("key", "value");
                }});
            }});
        }});

        Assertions.assertNotNull(createdNode);
        Assertions.assertEquals("single-value", yamlDocument.getScalarOrNull("mapping", "single").value());
        Assertions.assertEquals(100, yamlDocument.getScalarOrNull("mapping", "list", 0).value());
        Assertions.assertEquals("value", yamlDocument.getScalarOrNull("mapping", "list", 1, "key").value());
    }

    @Test
    void readBySerializer() throws Throwable {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlDocument yamlDocument = sparrowYaml.loadFromResource("full-test.yml");

        // 整数
        Integer integer1 = yamlDocument.getAs(int.class, "test", "int");
        Assertions.assertEquals(1314, integer1);
        // 字符串
        String string1 = yamlDocument.getAs(String.class, "test", "string-1");
        Assertions.assertEquals("test", string1);
        String quoted = yamlDocument.getAs(String.class, "test", "quoted");
        Assertions.assertEquals("So does this quoted scalar.\n", quoted);
        // 字符串列表
        List<String> stringList = yamlDocument.getAsList(String.class, "test", "string_list");
        Assertions.assertEquals(List.of("qwq", "awa", "fwf"), stringList);
    }

}
