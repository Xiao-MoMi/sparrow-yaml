package net.momirealms.sparrow.yaml.node;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NodeValueProjection {

    @Nullable
    static Object represent(@Nullable YamlNode<?> node) {
        if (node == null) return null;
        if (node instanceof SectionNode sectionNode) return representSection(sectionNode);
        if (node instanceof SequenceNode sequenceNode) return representSequence(sequenceNode);
        return node.value();
    }

    private static Map<Object, Object> representSection(SectionNode sectionNode) {
        Map<Object, Object> values = new LinkedHashMap<>(Math.max((int) (sectionNode.value().size() / 0.75f) + 1, 16));
        for (Map.Entry<Object, YamlNode<?>> entry : sectionNode.value().entrySet()) {
            values.put(entry.getKey(), represent(entry.getValue()));
        }
        return values;
    }

    private static List<Object> representSequence(SequenceNode sequenceNode) {
        List<YamlNode<?>> elements = sequenceNode.value();
        List<Object> values = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            values.add(represent(elements.get(i)));
        }
        return values;
    }
}
