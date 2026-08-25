package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionNodeSynchronizationTest {

    @Test
    void should_PreserveOrderAndKeyNode_When_ReplacingIndexedTuple() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument document = yaml.load("# keep key comment\nfirst: old\nsecond: value\n");
        MappingNode mappingNode = (MappingNode) document.internalValueNode();
        Node firstKeyNode = document.getScalarOrNull(Route.from("first")).internalKeyNode();

        document.setSubNode("first", "new");
        ScalarNode replacement = document.getScalarOrNull(Route.from("first"));

        assertEquals(List.of("first", "second"), List.copyOf(document.value().keySet()));
        assertEquals(2, mappingNode.getValue().size());
        assertSame(firstKeyNode, replacement.internalKeyNode());
        assertSame(firstKeyNode, mappingNode.getValue().get(0).getKeyNode());

        replacement.setValue("newer");
        assertSame(replacement.internalValueNode(), mappingNode.getValue().get(0).getValueNode());

        Collections.swap(mappingNode.getValue(), 0, 1);
        replacement.setValue("latest");
        assertSame(firstKeyNode, mappingNode.getValue().get(1).getKeyNode());
        assertSame(replacement.internalValueNode(), mappingNode.getValue().get(1).getValueNode());

        StringWriter writer = new StringWriter();
        document.save(writer);
        assertTrue(writer.toString().contains("# keep key comment\nfirst: latest"));
    }

    @Test
    void should_SkipUnchangedOrderAndRebuildChangedOrderOnce() throws IOException {
        YamlDocument document = SparrowYaml.builder().build().load("first: 1\nsecond: 2\nthird: 3\n");
        MappingNode mappingNode = (MappingNode) document.internalValueNode();
        List<NodeTuple> originalTuples = mappingNode.getValue();

        document.reorderKeys(List.of("first", "second", "third"));

        assertSame(originalTuples, mappingNode.getValue());

        document.reorderKeys(List.of("third", "first"));

        List<String> tupleKeys = new ArrayList<>();
        for (NodeTuple tuple : mappingNode.getValue()) {
            tupleKeys.add(((org.snakeyaml.engine.v2.nodes.ScalarNode) tuple.getKeyNode()).getValue());
        }
        assertEquals(List.of("third", "first", "second"), List.copyOf(document.value().keySet()));
        assertEquals(List.of("third", "first", "second"), tupleKeys);
    }

    @Test
    void should_RemoveKeysInOneBatchAndSynchronizeAliases() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument document = yaml.load("base: &base\n  keep: yes\n  remove-a: a\n  remove-b: b\nalias: *base\n");
        SectionNode base = document.getSectionOrNull(Route.from("base"));

        base.removeSubNodes(List.of("remove-a", "remove-b"));

        assertEquals(List.of("keep"), List.copyOf(base.value().keySet()));
        assertEquals(List.of("keep"), List.copyOf(document.getSectionOrNull(Route.from("alias")).value().keySet()));
        assertNull(document.getNodeOrNull(Route.from("alias", "remove-a")));
        assertEquals("yes", document.getString(Route.from("alias", "keep")));
        assertEquals(1, ((MappingNode) base.internalValueNode()).getValue().size());
    }

    @Test
    void should_KeepWrapperAndTupleCountsAlignedDuringBulkMutation() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument document = yaml.load("");
        List<String> keysToRemove = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            document.setSubNode("key-" + i, i);
            if ((i & 1) == 0) {
                keysToRemove.add("key-" + i);
            }
        }

        document.removeSubNodes(keysToRemove);
        for (int i = 1; i < 1_000; i += 2) {
            document.getScalarOrNull(Route.from("key-" + i)).setValue(i + 1);
        }

        assertEquals(500, document.value().size());
        assertEquals(500, ((MappingNode) document.internalValueNode()).getValue().size());
        assertNull(document.getNodeOrNull(Route.from("key-0")));
        assertEquals(2, document.getInt(Route.from("key-1")));

        StringWriter writer = new StringWriter();
        document.save(writer);
        YamlDocument reloaded = yaml.load(writer.toString());
        assertEquals(500, reloaded.value().size());
        assertEquals(1_000, reloaded.getInt(Route.from("key-999")));
    }

    @Test
    void should_ReorderAndCleanUpgradeSectionWithSingleLinearRebuild() throws IOException {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument local = yaml.load("config-version: 1\nthird: local-3\nfirst: local-1\nold-a: a\nold-b: b\n");
        YamlDocument defaults = yaml.load("config-version: 2\nfirst: default-1\nsecond: default-2\nthird: default-3\n");
        YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                .deleteRemovedNodes(true)
                .build();

        pipeline.upgrade(local, defaults);

        assertEquals(List.of("config-version", "first", "second", "third"), List.copyOf(local.value().keySet()));
        assertEquals(4, ((MappingNode) local.internalValueNode()).getValue().size());
        assertNull(local.getNodeOrNull(Route.from("old-a")));
        assertNull(local.getNodeOrNull(Route.from("old-b")));
        assertEquals("local-1", local.getString(Route.from("first")));
        assertEquals("default-2", local.getString(Route.from("second")));
    }

    @Test
    void should_ReplaceObjectAndDuplicateKeysWithoutAppendingTuples() throws IOException {
        SparrowYaml objectKeyYaml = SparrowYaml.builder().setAllowObjectKeys(true).build();
        YamlDocument objectKeyDocument = objectKeyYaml.load("");
        objectKeyDocument.setSubNode(1, "one");
        objectKeyDocument.setSubNode(1, "updated");

        assertEquals(1, ((MappingNode) objectKeyDocument.internalValueNode()).getValue().size());
        assertEquals("updated", objectKeyDocument.getString(Route.from(1)));

        SparrowYaml duplicateKeyYaml = SparrowYaml.builder().setAllowDuplicateKeys(true).build();
        YamlDocument duplicateKeyDocument = duplicateKeyYaml.load("key: first\nkey: second\n");
        duplicateKeyDocument.setSubNode("key", "third");

        assertEquals(1, ((MappingNode) duplicateKeyDocument.internalValueNode()).getValue().size());
        assertEquals("third", duplicateKeyDocument.getString(Route.from("key")));
    }
}
