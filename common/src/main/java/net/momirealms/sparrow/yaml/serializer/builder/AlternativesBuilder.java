package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.exception.AlternativesNodeException;
import net.momirealms.sparrow.yaml.exception.AlternativesNodeException.Branch;
import net.momirealms.sparrow.yaml.exception.AlternativesNodeException.Failure;
import net.momirealms.sparrow.yaml.exception.AlternativesNodeException.Shape;
import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 为同一个 Java 类型声明多个 YAML 写法候选.
 *
 * @param <T> 最终解码得到的 Java 类型
 */
public final class AlternativesBuilder<T> {
    private final Class<T> type;
    private final Map<String, Candidate<T>> branchesById = new LinkedHashMap<>();
    private final List<Candidate<T>> candidates = new ArrayList<>();
    private String writeAs;

    public AlternativesBuilder(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public AlternativesBuilder<T> mapping(
            String id,
            Function<? super MappingBuilder<T>, NodeSerializer<T>> builder
    ) {
        Objects.requireNonNull(builder, "builder");
        return add(id, Shape.MAPPING, builder.apply(new MappingBuilder<>(type)));
    }

    public AlternativesBuilder<T> sequence(
            String id,
            Function<? super SequenceBuilder<T>, NodeSerializer<T>> builder
    ) {
        Objects.requireNonNull(builder, "builder");
        return add(id, Shape.SEQUENCE, builder.apply(new SequenceBuilder<>(type)));
    }

    public AlternativesBuilder<T> scalar(String id, NodeSerializer<T> serializer) {
        return add(id, Shape.SCALAR, serializer);
    }

    public AlternativesBuilder<T> writeAs(String id) {
        this.writeAs = requireId(id);
        return this;
    }

    public NodeSerializer<T> build() {
        if (writeAs == null) {
            throw new IllegalStateException("writeAs(id) is required");
        }
        Candidate<T> canonical = branchesById.get(writeAs);
        if (canonical == null) {
            throw new IllegalArgumentException("Unknown alternatives branch id '" + writeAs + "'");
        }
        List<Candidate<T>> orderedCandidates = List.copyOf(candidates);
        return NodeSerializer.createInternal(
                type,
                node -> decode(node, orderedCandidates),
                value -> canonical.serializer().serialize(value)
        );
    }

    private AlternativesBuilder<T> add(String id, Shape shape, NodeSerializer<T> serializer) {
        String requiredId = requireId(id);
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(serializer, "serializer");
        if (branchesById.containsKey(requiredId)) {
            throw new IllegalArgumentException("Duplicate alternatives branch id '" + requiredId + "'");
        }
        Candidate<T> candidate = new Candidate<>(new Branch(requiredId, candidates.size(), shape), serializer);
        branchesById.put(requiredId, candidate);
        candidates.add(candidate);
        return this;
    }

    private T decode(YamlNode<?> node, List<Candidate<T>> orderedCandidates) {
        Shape shape = shapeOf(node);
        if (shape == null) {
            throw new InvalidNodeException(node, type);
        }

        List<Branch> branches = branchesOf(orderedCandidates);
        List<Failure> failures = new ArrayList<>();
        boolean matchedShape = false;
        for (Candidate<T> candidate : orderedCandidates) {
            if (candidate.branch().shape() != shape) {
                continue;
            }
            matchedShape = true;
            try {
                return candidate.serializer().deserialize(node);
            } catch (MissingNodeException | InvalidNodeException e) {
                failures.add(new Failure(candidate.branch(), e));
            }
        }

        if (!matchedShape) {
            throw new AlternativesNodeException(
                    node,
                    type,
                    shape,
                    branches,
                    List.of(),
                    "No alternatives branch matched YAML shape '" + shape.name().toLowerCase() + "'. Registered branches: " + branchIds(branches)
            );
        }
        if (failures.size() == 1) {
            throw failures.get(0).exception();
        }
        throw new AlternativesNodeException(
                node,
                type,
                shape,
                branches,
                failures,
                "Alternatives candidates failed: " + failureSummary(failures)
        );
    }

    private static Shape shapeOf(YamlNode<?> node) {
        if (node instanceof SectionNode) {
            return Shape.MAPPING;
        }
        if (node instanceof SequenceNode) {
            return Shape.SEQUENCE;
        }
        if (node instanceof ScalarNode) {
            return Shape.SCALAR;
        }
        return null;
    }

    private static List<Branch> branchesOf(List<? extends Candidate<?>> candidates) {
        List<Branch> branches = new ArrayList<>(candidates.size());
        for (Candidate<?> candidate : candidates) {
            branches.add(candidate.branch());
        }
        return branches;
    }

    private static String branchIds(List<Branch> branches) {
        List<String> ids = new ArrayList<>(branches.size());
        for (Branch branch : branches) {
            ids.add(branch.id());
        }
        return String.join(", ", ids);
    }

    private static String failureSummary(List<Failure> failures) {
        List<String> messages = new ArrayList<>(failures.size());
        for (Failure failure : failures) {
            messages.add(failure.branch().id() + ": " + failure.message());
        }
        return String.join("; ", messages);
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return id;
    }

    private record Candidate<T>(Branch branch, NodeSerializer<T> serializer) {
    }
}
