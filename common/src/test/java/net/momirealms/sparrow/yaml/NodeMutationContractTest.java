package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.patch.RelocateRule;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.nodes.CollectionNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeMutationContractTest {

    @Test
    void should_ExposeReadOnlyParentValues() throws IOException {
        YamlDocument document = SparrowYaml.create().load("section: {value: old}\nsequence: [old]\n");
        SectionNode section = document.getSectionOrNull(Route.from("section"));
        SequenceNode sequence = document.getSequenceOrNull(Route.from("sequence"));

        assertThrows(UnsupportedOperationException.class, section.value()::clear);
        assertThrows(UnsupportedOperationException.class, sequence.value()::clear);
        assertEquals("old", document.getString(Route.from("section", "value")));
        assertEquals("old", document.getString(Route.from("sequence", 0)));
    }

    @Test
    void should_PreserveSequenceNullPositionsAcrossDumpAndReload() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument sparse = yaml.load("items: [a]\n");
        sparse.getSequenceOrNull(Route.from("items")).setSubNode(3, "d");

        YamlDocument sparseReloaded = yaml.load(sparse.dumpToString());
        assertEquals(Arrays.asList("a", null, null, "d"), sparseReloaded.getSequenceOrNull(Route.from("items")).getValues());
        assertEquals("d", sparseReloaded.getString(Route.from("items", 3)));

        YamlDocument explicit = yaml.load("items: [a, null, c]\n");
        YamlDocument explicitReloaded = yaml.load(explicit.dumpToString());
        assertEquals(Arrays.asList("a", null, "c"), explicitReloaded.getSequenceOrNull(Route.from("items")).getValues());
    }

    @Test
    void should_ReplaceSectionContentsByCopyAndSynchronizeAliases() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument source = yaml.load("first: one\nsecond: {nested: two}\n");
        YamlDocument target = yaml.load("base: &shared {old: value}\nalias: *shared\n");
        SectionNode base = target.getSectionOrNull(Route.from("base"));
        Map<Object, YamlNode<?>> replacement = new LinkedHashMap<>(source.value());

        base.setValue(replacement);

        assertSame(source, source.getNodeOrNull(Route.from("first")).root());
        assertSame(target, base.getNodeOrNull(Route.from("first")).root());
        assertNotSame(source.getNodeOrNull(Route.from("first")), base.getNodeOrNull(Route.from("first")));
        assertSame(base.internalValueNode(), target.getSectionOrNull(Route.from("alias")).internalValueNode());
        assertEquals("one", target.getString(Route.from("alias", "first")));
        assertEquals("two", target.getString(Route.from("alias", "second", "nested")));
        assertNull(target.getNodeOrNull(Route.from("base", "old")));

        YamlDocument reloaded = yaml.load(target.dumpToString());
        assertEquals("one", reloaded.getString(Route.from("base", "first")));
        assertEquals("one", reloaded.getString(Route.from("alias", "first")));
    }

    @Test
    void should_ReplaceSequenceContentsByCopyAndSynchronizeAliases() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument source = yaml.load("items: [one, {nested: two}]\n");
        YamlDocument target = yaml.load("base: &shared [old]\nalias: *shared\n");
        SequenceNode sourceItems = source.getSequenceOrNull(Route.from("items"));
        SequenceNode base = target.getSequenceOrNull(Route.from("base"));

        base.setValue(new ArrayList<>(sourceItems.value()));

        assertSame(source, sourceItems.getNodeOrNull(Route.from(0)).root());
        assertSame(target, base.getNodeOrNull(Route.from(0)).root());
        assertNotSame(sourceItems.getNodeOrNull(Route.from(0)), base.getNodeOrNull(Route.from(0)));
        assertSame(base.internalValueNode(), target.getSequenceOrNull(Route.from("alias")).internalValueNode());
        assertEquals(List.of("one", Map.of("nested", "two")), target.getSequenceOrNull(Route.from("alias")).getValues());

        YamlDocument reloaded = yaml.load(target.dumpToString());
        assertEquals(List.of("one", Map.of("nested", "two")), reloaded.getSequenceOrNull(Route.from("base")).getValues());
    }

    @Test
    void should_PreserveExistingMetadataWhenReplacingRawValue() throws IOException {
        YamlDocument document = SparrowYaml.create().load("# Target key\ntarget: {old: value} # Target inline\n");

        document.setSubNode("target", List.of("new"));
        SequenceNode replacement = document.getSequenceOrNull(Route.from("target"));

        assertEquals("Target key", replacement.beforeKeyComments().get(0).getValue().trim());
        assertEquals("Target inline", replacement.inlineValueComments().get(0).getValue().trim());
        assertEquals(FlowStyle.FLOW, ((CollectionNode<?>) replacement.internalValueNode()).getFlowStyle());
    }

    @Test
    void should_MoveWithinDocumentAndPreserveAliasIdentity() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.load("# Shared key\nbase: &shared {value: old} # Shared inline\nalias: *shared\n");
        YamlDocument defaults = yaml.createDocument();

        new RelocateRule(Route.from("base"), Route.from("moved")).apply(defaults, document);

        SectionNode moved = document.getSectionOrNull(Route.from("moved"));
        SectionNode alias = document.getSectionOrNull(Route.from("alias"));
        assertNull(document.getNodeOrNull(Route.from("base")));
        assertSame(moved.internalValueNode(), alias.internalValueNode());
        assertEquals("Shared key", moved.beforeKeyComments().get(0).getValue().trim());
        assertEquals("Shared inline", moved.inlineValueComments().get(0).getValue().trim());
        assertEquals(FlowStyle.FLOW, ((CollectionNode<?>) moved.internalValueNode()).getFlowStyle());

        moved.getScalarOrNull(Route.from("value")).setValue("new");
        assertEquals("new", document.getString(Route.from("alias", "value")));
        String dumped = document.dumpToString();
        assertTrue(dumped.contains("*"));
        assertEquals("new", yaml.load(dumped).getString(Route.from("moved", "value")));
    }

    @Test
    void should_KeepDetachedNodeReadableAndCopyable() throws IOException {
        YamlDocument document = SparrowYaml.create().load("# Detached key\nsource: {value: old}\n");
        YamlNode<?> removed = document.removeNode(Route.from("source"));

        assertNull(removed.parentNode());
        assertNull(removed.route());
        assertEquals("Detached key", removed.beforeKeyComments().get(0).getValue().trim());
        assertEquals(Map.of("value", "old"), removed.representValue());

        document.setSubNode("copy", removed);
        YamlNode<?> copy = document.getNodeOrNull(Route.from("copy"));
        assertNull(removed.parentNode());
        assertNotSame(removed, copy);
        assertNotSame(removed.internalValueNode(), copy.internalValueNode());
        assertEquals("old", document.getString(Route.from("copy", "value")));
    }

    @Test
    void should_CopyNestedAliasGraphWithoutSharingSourceIdentity() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument source = yaml.load("container:\n  base: &shared {value: old}\n  alias: *shared\n");
        YamlDocument target = yaml.createDocument();

        target.setSubNode("copy", source.getSectionOrNull(Route.from("container")));
        SectionNode sourceBase = source.getSectionOrNull(Route.from("container", "base"));
        SectionNode copiedBase = target.getSectionOrNull(Route.from("copy", "base"));
        SectionNode copiedAlias = target.getSectionOrNull(Route.from("copy", "alias"));

        assertSame(copiedBase.internalValueNode(), copiedAlias.internalValueNode());
        assertNotSame(sourceBase.internalValueNode(), copiedBase.internalValueNode());
        copiedBase.getScalarOrNull(Route.from("value")).setValue("new");
        assertEquals("new", target.getString(Route.from("copy", "alias", "value")));
        assertEquals("old", source.getString(Route.from("container", "base", "value")));
        assertTrue(target.dumpToString().contains("*"));
    }

    @Test
    void should_CopyScalarFormattingAndComments() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument source = yaml.load("""
                source:
                  # Quoted key
                  quoted: '001' # Quoted inline
                  block: |-
                    first
                    second
                """);
        YamlDocument target = yaml.createDocument();

        target.setSubNode("copy", source.getSectionOrNull(Route.from("source")));
        String dumped = target.dumpToString();

        assertTrue(dumped.contains("# Quoted key"));
        assertTrue(dumped.contains("quoted: '001' # Quoted inline"));
        assertTrue(dumped.contains("block: |-"));
        assertEquals("first\nsecond", yaml.load(dumped).getString(Route.from("copy", "block")));
    }

    @Test
    void should_ReplaceOneAliasOccurrenceWithoutChangingSharedValue() throws IOException {
        YamlDocument document = SparrowYaml.create().load("base: &shared {value: old}\nalias: *shared\n");

        document.setSubNode("alias", Map.of("own", true));

        assertEquals("old", document.getString(Route.from("base", "value")));
        assertEquals(true, document.getBoolean(Route.from("alias", "own")));
        assertNotSame(
                document.getNodeOrNull(Route.from("base")).internalValueNode(),
                document.getNodeOrNull(Route.from("alias")).internalValueNode()
        );
    }

    @Test
    void should_RejectMovingNodeIntoItsDescendantWithoutMutation() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.load("section: {child: value}\n");
        YamlDocument aliasDocument = yaml.load("base: &shared {child: value}\nalias: *shared\n");
        String before = document.dumpToString();
        String aliasBefore = aliasDocument.dumpToString();

        assertThrows(
                IllegalArgumentException.class,
                () -> new RelocateRule(Route.from("section"), Route.from("section", "nested")).apply(yaml.createDocument(), document)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RelocateRule(Route.from("base"), Route.from("alias", "nested")).apply(yaml.createDocument(), aliasDocument)
        );

        assertEquals(before, document.dumpToString());
        assertEquals(aliasBefore, aliasDocument.dumpToString());
        assertEquals("value", document.getString(Route.from("section", "child")));
        assertEquals("value", aliasDocument.getString(Route.from("base", "child")));
    }

    @Test
    void should_KeepAstAndWrapperViewsAlignedAcrossMutations() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.load("base: &shared {first: 1, second: 2}\nalias: *shared\nitems: [a]\n");
        SectionNode base = document.getSectionOrNull(Route.from("base"));
        SequenceNode items = document.getSequenceOrNull(Route.from("items"));

        base.setSubNode("third", 3);
        base.reorderKeys(List.of("second", "first"));
        base.getScalarOrNull(Route.from("first")).setValue(10);
        items.setSubNode(3, "d");
        items.removeSubNode(0);
        new RelocateRule(Route.from("base"), Route.from("moved")).apply(yaml.createDocument(), document);

        assertAligned(document);
    }

    @Test
    void should_LeaveDocumentsUntouchedWhenCollectionReplacementCannotBePrepared() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument source = yaml.load("source: copied\n");
        YamlDocument target = yaml.load("base: {old: value}\n");
        SectionNode base = target.getSectionOrNull(Route.from("base"));
        String before = target.dumpToString();
        Map<Object, YamlNode<?>> invalid = new LinkedHashMap<>();
        invalid.put("copied", source.getNodeOrNull(Route.from("source")));
        invalid.put(null, source.getNodeOrNull(Route.from("source")));

        assertThrows(NullPointerException.class, () -> base.setValue(invalid));

        assertEquals(before, target.dumpToString());
        assertEquals("value", target.getString(Route.from("base", "old")));
        assertSame(source, source.getNodeOrNull(Route.from("source")).root());
        assertFalse(source.isEmptyDocument());
    }

    private static void assertAligned(YamlNode<?> node) {
        if (node instanceof SectionNode sectionNode) {
            List<NodeTuple> tuples = ((MappingNode) sectionNode.internalValueNode()).getValue();
            assertEquals(sectionNode.value().size(), tuples.size());
            int index = 0;
            for (Map.Entry<Object, YamlNode<?>> entry : sectionNode.value().entrySet()) {
                YamlNode<?> child = entry.getValue();
                NodeTuple tuple = tuples.get(index++);
                assertSame(sectionNode, child.parentNode());
                assertEquals(entry.getKey(), child.key());
                assertSame(child.internalKeyNode(), tuple.getKeyNode());
                assertSame(child.internalValueNode(), tuple.getValueNode());
                assertAligned(child);
            }
        } else if (node instanceof SequenceNode sequenceNode) {
            List<org.snakeyaml.engine.v2.nodes.Node> elements =
                    ((org.snakeyaml.engine.v2.nodes.SequenceNode) sequenceNode.internalValueNode()).getValue();
            assertEquals(sequenceNode.value().size(), elements.size());
            for (int i = 0; i < sequenceNode.value().size(); i++) {
                YamlNode<?> child = sequenceNode.value().get(i);
                assertSame(sequenceNode, child.parentNode());
                assertEquals(i, child.key());
                assertSame(child.internalValueNode(), elements.get(i));
                assertAligned(child);
            }
        }
    }
}
