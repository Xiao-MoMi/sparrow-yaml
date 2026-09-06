package net.momirealms.sparrow.yaml.route;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.route.element.IndexElement;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTest {

    @Test
    void should_NormalizeBuiltInRouteElementsAsValues() {
        Route plainKey = Route.from("key");
        Route explicitKey = Route.from(new KeyElement("key"));
        Route automaticIndex = Route.from(1);
        Route explicitIndex = Route.from(new IndexElement(1));

        assertEquals(plainKey, explicitKey);
        assertEquals(plainKey.hashCode(), explicitKey.hashCode());
        assertEquals(automaticIndex, explicitIndex);
        assertEquals(automaticIndex.hashCode(), explicitIndex.hashCode());
        assertArrayEquals(new Object[] {"key"}, explicitKey.routeKeys());
        assertArrayEquals(new Object[] {1}, explicitIndex.routeKeys());
        assertEquals("\"key\"", Route.pathName(explicitKey));
        assertEquals("\"[1]\"", Route.pathName(explicitIndex));
    }

    @Test
    void should_DistinguishMappingKeysFromSequenceIndexes() {
        Route mappingKey = Route.from(new KeyElement(1));
        Route sequenceIndex = Route.from(1);
        Map<Route, String> values = new HashMap<>();

        values.put(mappingKey, "mapping");
        values.put(sequenceIndex, "sequence");

        assertNotEquals(mappingKey, sequenceIndex);
        assertEquals(2, values.size());
        assertEquals("mapping", values.get(Route.from(new KeyElement(1))));
        assertEquals("sequence", values.get(Route.from(new IndexElement(1))));
        assertEquals("\"1\"", Route.pathName(mappingKey));
        assertEquals("\"[1]\"", Route.pathName(sequenceIndex));
        assertFalse(mappingKey.isParentRouteOf(sequenceIndex.add("child")));
        assertTrue(mappingKey.isParentRouteOf(mappingKey.add("child")));
    }

    @Test
    void should_ParseDottedPathsAsStringKeys() {
        Route numeric = Route.dotted("items.0.name");

        assertEquals(Route.from("server", "host"), Route.dotted("server.host"));
        assertEquals(Route.from("server"), Route.dotted("server"));
        assertArrayEquals(new Object[] {" a ", " b "}, Route.dotted(" a . b ").routeKeys());
        assertArrayEquals(new Object[] {"", "a"}, Route.dotted(".a").routeKeys());
        assertArrayEquals(new Object[] {"a", "", "b"}, Route.dotted("a..b").routeKeys());
        assertArrayEquals(new Object[] {"a", ""}, Route.dotted("a.").routeKeys());
        assertArrayEquals(new Object[] {"", ""}, Route.dotted(".").routeKeys());
        assertInstanceOf(KeyElement.class, numeric.getRouteElement(1));
        assertEquals(Route.from("items", "0", "name"), numeric);
        assertNotEquals(Route.from("items", 0, "name"), numeric);
    }

    @Test
    void should_KeepEmptyAndLiteralPathContractsSeparate() {
        assertThrows(IllegalArgumentException.class, () -> Route.dotted(""));
        assertEquals(0, Route.empty().length());
        assertEquals(1, Route.from("a.b").length());
        assertEquals(2, Route.dotted("a.b").length());
    }

    @Test
    void should_ReadDottedMappingPathsWithoutInferringIndexes() throws IOException {
        YamlDocument document = SparrowYaml.builder().build().load("""
                "a.b": literal
                a:
                  b: nested
                map:
                  "0": numeric-key
                items:
                  - sequence
                "":
                  "": empty-key
                """);

        assertEquals("literal", document.getString(Route.from("a.b")));
        assertEquals("nested", document.getString(Route.dotted("a.b")));
        assertEquals("numeric-key", document.getString(Route.dotted("map.0")));
        assertNull(document.getString(Route.dotted("items.0")));
        assertEquals("sequence", document.getString(Route.from("items", 0)));
        assertEquals("empty-key", document.getString(Route.dotted(".")));
    }

    @Test
    void should_PreserveExplicitElementKindsAcrossRouteOperations() {
        Route route = new Route(new KeyElement(1))
                .add(new IndexElement(2))
                .add(Route.from(new KeyElement(3)));

        assertInstanceOf(KeyElement.class, route.getRouteElement(0));
        assertInstanceOf(IndexElement.class, route.getRouteElement(1));
        assertInstanceOf(KeyElement.class, route.getRouteElement(2));
        assertEquals(Route.from(new KeyElement(1), new IndexElement(2)), route.parent());
        assertEquals(Route.from(new KeyElement(1)), Route.addTo(null, new KeyElement(1)));
        assertArrayEquals(new Object[] {1, 2, 3}, route.routeKeys());
        assertEquals("\"1[2].3\"", Route.pathName(route));
    }

    @Test
    void should_TreatNegativeIntegersAsKeysAndNegativePositionsAsOutOfBounds() {
        Route route = new Route(-1);

        assertInstanceOf(KeyElement.class, route.getRouteElement(0));
        assertEquals("-1", route.getRouteKey(0));
        assertEquals(Route.from(-1), Route.addTo(null, -1));
        assertNull(route.getRouteElementOrNull(-1));
        assertNull(route.getRouteElementOrNull(route.length()));
    }

    @Test
    void should_RepresentDocumentRootWithEmptyRoute() throws IOException {
        YamlDocument document = SparrowYaml.builder().build().load("");

        assertEquals(Route.empty(), document.route());
        assertEquals(Route.empty(), document.getKeyAsRoute());
        assertNull(document.keyRouteElement());
        assertNull(Route.empty().parent());
        assertEquals("<root>", Route.pathName(Route.empty()));
    }

    @Test
    void should_BindNumericNodeKeysToTheirParentNodeType() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().setAllowObjectKeys(true).build();
        YamlDocument document = yaml.load("1:\n  child: mapping\nitems:\n  - name: sequence\n");

        SectionNode mapping = document.getSectionOrNull(Route.from(new KeyElement(1)));
        SectionNode sequenceElement = document.getSectionOrNull(Route.from("items", 0));

        assertEquals(Route.from(new KeyElement(1)), mapping.route());
        assertEquals(Route.from(new KeyElement(1)), mapping.getKeyAsRoute());
        assertInstanceOf(KeyElement.class, mapping.route().getLastRouteElement());
        assertEquals(Route.from(new KeyElement(1), new KeyElement(2)), mapping.getSubRoute(2));
        assertInstanceOf(IndexElement.class, sequenceElement.route().getLastRouteElement());
        assertEquals(Route.from(new IndexElement(0)), sequenceElement.getKeyAsRoute());
        assertNull(document.getNodeOrNull(Route.from("items", new KeyElement(0))));
    }

    @Test
    void should_RespectExplicitMappingKeysWhenWritingAndRemoving() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument removalDocument = yaml.load("items: [first, second]");
        YamlDocument writeDocument = yaml.load("items: [first, second]");
        Route mappingKeyRoute = Route.from("items", new KeyElement(0));

        assertNull(removalDocument.removeNode(mappingKeyRoute));
        assertEquals("first", removalDocument.get(String.class, Route.from("items", 0)));
        assertEquals("second", removalDocument.get(String.class, Route.from("items", 1)));

        writeDocument.setAndGet(mappingKeyRoute, "changed");
        assertInstanceOf(SectionNode.class, writeDocument.getNodeOrNull(Route.from("items")));
        assertEquals("changed", writeDocument.get(String.class, mappingKeyRoute));
    }
}
