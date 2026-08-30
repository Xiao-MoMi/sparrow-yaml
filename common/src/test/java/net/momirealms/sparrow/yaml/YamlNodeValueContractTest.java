package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.nodes.CollectionNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlNodeValueContractTest {

    @Test
    void should_ReplaceNonRootNodesBySerializedShapeAndPreserveSurfaceMetadata() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        NodeSerializer<Point> pointSerializer = pointSerializer();
        TypeRef<List<String>> stringListType = new TypeRef<>() {};
        yaml.serializers().register(Point.class, pointSerializer);
        yaml.serializers().register(stringListType, NodeSerializers.STRING.listOf());
        YamlDocument document = yaml.load("""
                # Scalar key comment
                scalar: old # Scalar inline comment
                mapping: {old: value}
                sequence: [old]
                items: [old]
                nullable: old
                """);

        YamlNode<?> originalScalar = document.getNodeOrNull(Route.from("scalar"));
        originalScalar.set(Point.class, new Point(3, 4));
        SectionNode mappingReplacement = document.getSectionOrNull(Route.from("scalar"));

        assertNotSame(originalScalar, mappingReplacement);
        assertNull(originalScalar.parentNode());
        assertEquals(3, document.get(Integer.class, Route.from("scalar", "x")));
        assertEquals(4, document.get(Integer.class, Route.from("scalar", "y")));
        assertEquals("Scalar key comment", mappingReplacement.beforeKeyComments().get(0).getValue().trim());
        assertEquals("Scalar inline comment", mappingReplacement.inlineValueComments().get(0).getValue().trim());

        YamlNode<?> originalMapping = document.getNodeOrNull(Route.from("mapping"));
        originalMapping.set(stringListType, List.of("first", "second"));
        SequenceNode sequenceReplacement = document.getSequenceOrNull(Route.from("mapping"));

        assertNotSame(originalMapping, sequenceReplacement);
        assertNull(originalMapping.parentNode());
        assertEquals(List.of("first", "second"), sequenceReplacement.getValues());
        assertEquals(FlowStyle.FLOW, ((CollectionNode<?>) sequenceReplacement.internalValueNode()).getFlowStyle());

        YamlNode<?> originalSequence = document.getNodeOrNull(Route.from("sequence"));
        ScalarNode scalarReplacement = assertInstanceOf(
                ScalarNode.class,
                document.setAndGet(NodeSerializers.STRING, "done", Route.from("sequence"))
        );

        assertNotSame(originalSequence, scalarReplacement);
        assertEquals("done", scalarReplacement.value());

        SequenceNode items = document.getSequenceOrNull(Route.from("items"));
        YamlNode<?> originalItem = items.getNodeOrNull(Route.from(0));
        originalItem.set(pointSerializer, new Point(5, 6));
        SectionNode itemReplacement = items.getSectionOrNull(Route.from(0));

        assertNotSame(originalItem, itemReplacement);
        assertNull(originalItem.parentNode());
        assertEquals(5, itemReplacement.get(Integer.class, Route.from("x")));
        assertEquals(6, itemReplacement.get(Integer.class, Route.from("y")));

        ScalarNode nullable = document.getScalarOrNull(Route.from("nullable"));
        nullable.set(String.class, null);
        assertNull(document.getScalarOrNull(Route.from("nullable")).value());
    }

    @Test
    void should_ReplaceRootOnlyWhenSerializerProducesMapping() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        yaml.serializers().register(Point.class, pointSerializer());
        YamlDocument document = yaml.load("{old: value}");

        document.set(Point.class, new Point(7, 8));

        assertTrue(document.isRoot());
        assertNull(document.getNodeOrNull(Route.from("old")));
        assertEquals(7, document.get(Integer.class, Route.from("x")));
        assertEquals(8, document.get(Integer.class, Route.from("y")));
        assertEquals(FlowStyle.FLOW, ((CollectionNode<?>) document.internalValueNode()).getFlowStyle());
    }

    @Test
    void should_RejectNonMappingRootReplacementWithoutChangingDocument() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        TypeRef<List<String>> stringListType = new TypeRef<>() {};
        yaml.serializers().register(stringListType, NodeSerializers.STRING.listOf());
        YamlDocument document = yaml.load("existing: value");
        String before = document.dumpToString();

        assertThrows(IllegalArgumentException.class, () -> document.set(stringListType, List.of("invalid")));
        assertThrows(IllegalArgumentException.class, () -> document.set(String.class, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> document.set(NodeSerializers.STRING, "invalid"));

        assertEquals(before, document.dumpToString());
        assertEquals("value", document.get(String.class, Route.from("existing")));
    }

    @Test
    void should_SeparateStringKeyRawValuesFromRepresentedObjectKeys() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().setAllowObjectKeys(true).build();
        YamlDocument document = yaml.load("""
                1:
                  nested-null: null
                sequence:
                  - null
                  - 2: value
                typed:
                  3: value
                """);

        Map<String, Object> values = document.getValues();
        Map<?, ?> numericSection = assertInstanceOf(Map.class, values.get("1"));
        List<?> sequenceValues = assertInstanceOf(List.class, values.get("sequence"));
        Map<?, ?> sequenceValuesSection = assertInstanceOf(Map.class, sequenceValues.get(1));
        Map<?, ?> typedValues = assertInstanceOf(Map.class, values.get("typed"));
        Map<?, ?> represented = assertInstanceOf(Map.class, document.representValue());
        List<?> representedSequence = assertInstanceOf(List.class, represented.get("sequence"));
        Map<?, ?> representedSequenceSection = assertInstanceOf(Map.class, representedSequence.get(1));
        Map<?, ?> representedTyped = assertInstanceOf(Map.class, represented.get("typed"));
        SequenceNode sequenceNode = document.getSequenceOrNull(Route.from("sequence"));

        assertTrue(values.containsKey("1"));
        assertFalse(values.containsKey(1));
        assertTrue(numericSection.containsKey("nested-null"));
        assertNull(numericSection.get("nested-null"));
        assertNull(sequenceValues.get(0));
        assertEquals("value", sequenceValuesSection.get("2"));
        assertEquals("value", typedValues.get("3"));
        assertTrue(represented.containsKey(1));
        assertEquals("value", representedSequenceSection.get(2));
        assertEquals("value", representedTyped.get(3));
        assertEquals("value", assertInstanceOf(Map.class, sequenceNode.getValues().get(1)).get("2"));
        assertEquals("value", assertInstanceOf(Map.class, assertInstanceOf(List.class, sequenceNode.representValue()).get(1)).get(2));
        assertEquals(Map.of(3, "value"), document.get(NodeSerializers.STRING.mapOf(NodeSerializers.INT), Route.from("typed")));
        assertThrows(InvalidNodeException.class, () -> document.get(NodeSerializers.OBJECT));
    }

    private static NodeSerializer<Point> pointSerializer() {
        return NodeSerializers.mapping(Point.class)
                .group(
                        NodeSerializers.INT.required("x").forGetter(Point::x),
                        NodeSerializers.INT.required("y").forGetter(Point::y)
                )
                .apply(Point::new);
    }

    private record Point(int x, int y) {
    }
}
