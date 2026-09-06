package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.node.AbstractYamlNode;
import net.momirealms.sparrow.yaml.node.NodeMutation;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class YamlDocument extends SectionNode {
    private final SparrowYaml sparrowYaml;
    private final Map<Node, List<AbstractYamlNode<?>>> nodeReferences = new IdentityHashMap<>();

    YamlDocument(@NotNull SparrowYaml sparrowYaml) {
        super(null, null, createEmptyRoot(sparrowYaml), null, new ExtendedConstructor(sparrowYaml.loadSettings()));
        this.sparrowYaml = sparrowYaml;
    }

    /**
     * 从输入流创建 YAML 文档.
     *
     * @param sparrowYaml 提供解析与表示设置的 SparrowYaml
     * @param inputStream YAML 输入流
     * @throws IOException 当输入流读取失败时
     */
    public YamlDocument(@NotNull SparrowYaml sparrowYaml, @NotNull InputStream inputStream) throws IOException {
        this(sparrowYaml);
        this.reload(inputStream);
    }

    /**
     * 获取生产该文档对象的 SparrowYaml 对象;
     * @return SparrowYaml;
     */
    public SparrowYaml sparrowYaml() {
        return sparrowYaml;
    }

    @ApiStatus.Internal
    public void attachNode(AbstractYamlNode<?> node) {
        this.registerNode(node);
        if (node instanceof SectionNode sectionNode) {
            for (YamlNode<?> child : sectionNode.value().values()) {
                this.attachNode((AbstractYamlNode<?>) child);
            }
        } else if (node instanceof net.momirealms.sparrow.yaml.node.SequenceNode sequenceNode) {
            for (YamlNode<?> child : sequenceNode.value()) {
                this.attachNode((AbstractYamlNode<?>) child);
            }
        }
    }

    @ApiStatus.Internal
    public void attachNodeShallow(AbstractYamlNode<?> node) {
        this.registerNode(node);
    }

    @ApiStatus.Internal
    public void detachNode(AbstractYamlNode<?> node) {
        if (node instanceof SectionNode sectionNode) {
            for (YamlNode<?> child : sectionNode.value().values()) {
                this.detachNode((AbstractYamlNode<?>) child);
            }
        } else if (node instanceof net.momirealms.sparrow.yaml.node.SequenceNode sequenceNode) {
            for (YamlNode<?> child : sequenceNode.value()) {
                this.detachNode((AbstractYamlNode<?>) child);
            }
        }
        this.unregisterNode(node, node.internalValueNode());
    }

    @ApiStatus.Internal
    public void rebindNode(AbstractYamlNode<?> node, @Nullable Node previousNode) {
        this.unregisterNode(node, previousNode);
        this.registerNode(node);
    }

    @ApiStatus.Internal
    public List<AbstractYamlNode<?>> nodeReferences(AbstractYamlNode<?> node) {
        Node internalNode = node.internalValueNode();
        if (internalNode == null) {
            return List.of(node);
        }
        List<AbstractYamlNode<?>> references = this.nodeReferences.get(internalNode);
        return references == null || references.isEmpty() ? List.of(node) : List.copyOf(references);
    }

    private void registerNode(AbstractYamlNode<?> node) {
        Node internalNode = node.internalValueNode();
        if (internalNode == null) {
            return;
        }
        List<AbstractYamlNode<?>> references = this.nodeReferences.computeIfAbsent(internalNode, ignored -> new ArrayList<>());
        if (!references.contains(node)) {
            references.add(node);
        }
    }

    private void unregisterNode(AbstractYamlNode<?> node, @Nullable Node internalNode) {
        if (internalNode == null) {
            return;
        }
        List<AbstractYamlNode<?>> references = this.nodeReferences.get(internalNode);
        if (references == null) {
            return;
        }
        references.remove(node);
        if (references.isEmpty()) {
            this.nodeReferences.remove(internalNode);
        }
    }

    /**
     * 检查该文档是否是一个没有任何内容的空文档;
     * @return 是否是空文档;
     */
    public boolean isEmptyDocument() {
        MappingNode node = (MappingNode) this.internalValueNode();
        return node == null || node.getValue().isEmpty();
    }

    /**
     * 从输入流重新加载文档, 空输入清空当前内容, 解析失败时保留原状态.
     *
     * @param inputStream YAML 输入流
     * @throws IOException 当输入流读取失败时
     */
    public void reload(@NotNull InputStream inputStream) throws IOException {
        YamlDocument replacement = this.readReplacement(inputStream);
        this.replaceRoot(replacement);
    }

    private YamlDocument readReplacement(InputStream inputStream) throws IOException {
        LoadSettings loadSettings = this.sparrowYaml().loadSettings();
        Parser parser = new ParserImpl(loadSettings, new StreamReader(loadSettings, new YamlUnicodeReader(inputStream)));
        Composer composer = new Composer(loadSettings, parser);
        ExtendedConstructor constructor = new ExtendedConstructor(loadSettings);
        MappingNode mappingNode = createEmptyRoot(this.sparrowYaml);

        if (composer.hasNext()) {
            Node node = composer.next();
            if (composer.hasNext()) {
                throw new InvalidObjectException("暂不支持多文档解析!");
            }
            if (!(node instanceof MappingNode parsedMappingNode)) {
                throw new IllegalArgumentException(String.format("根节点对象不是MappingNode！解析节点: %s", node.toString()));
            }
            constructor.constructSingleDocument(Optional.of(node));
            mappingNode = parsedMappingNode;
        }

        YamlDocument replacement = new YamlDocument(this.sparrowYaml);
        replacement.valueNode = mappingNode;
        try {
            replacement.init(null, mappingNode, constructor);
            return replacement;
        } finally {
            constructor.clear();
        }
    }

    private void replaceRoot(YamlDocument replacement) {
        NodeMutation.adoptRoot(this, replacement);
    }

    @ApiStatus.Internal
    public void replaceRootValue(@NotNull Map<?, ?> value) {
        NodeMutation.replaceRootValue(this, value);
    }

    private static MappingNode createEmptyRoot(SparrowYaml sparrowYaml) {
        return (MappingNode) sparrowYaml.standardRepresenter().represent(new LinkedHashMap<>());
    }

    /**
     * 将当前 YamlDocument 保存到文件中;
     * @param file 目标文件
     */
    public void save(File file) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(file)) {
            save(outputStream);
        }
    }

    /**
     * 将当前 YamlDocument 保存到 Path;
     * @param path 目标路径
     */
    public void save(Path path) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            save(outputStream);
        }
    }

    /**
     * 将当前 YamlDocument 保存到 OutputStream 中;
     * @param outputStream 目标流
     */
    public void save(OutputStream outputStream) throws IOException {
        save(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    /**
     * 将当前 YamlDocument 保存到 Writer.
     * @param writer 目标 Writer
     * @throws IOException 当 Writer 写入或刷新失败时
     */
    public void save(@NotNull Writer writer) throws IOException {
        try {
            this.dump(new WriterStreamDataWriter(writer));
        } catch (WriterIOException exception) {
            throw exception.ioException();
        }
    }

    /**
     * 将当前文档序列化为字符串.
     *
     * @return 完整 YAML 文本
     */
    @NotNull
    public String dumpToString() {
        StringStreamDataWriter writer = new StringStreamDataWriter();
        this.dump(writer);
        return writer.content();
    }

    private void dump(StreamDataWriter streamDataWriter) {
        Dump dump = new Dump(this.sparrowYaml().dumpSettings());
        List<Runnable> restorers = new ArrayList<>();
        try {
            prepareDumpNode(this, restorers);
            dump.dumpNode(this.internalValueNode(), streamDataWriter);
        } finally {
            for (int i = restorers.size() - 1; i >= 0; i--) {
                restorers.get(i).run();
            }
        }
    }

    private static void prepareDumpNode(YamlNode<?> yamlNode, List<Runnable> restorers) {
        if (yamlNode instanceof SectionNode sectionNode) {
            prepareSectionNode(sectionNode, restorers);
        } else if (yamlNode instanceof net.momirealms.sparrow.yaml.node.SequenceNode sequenceNode) {
            prepareSequenceNode(sequenceNode, restorers);
        }
    }

    private static void prepareSectionNode(SectionNode sectionNode, List<Runnable> restorers) {
        MappingNode mappingNode = (MappingNode) sectionNode.internalValueNode();
        List<NodeTuple> original = new ArrayList<>(mappingNode.getValue());
        List<NodeTuple> filtered = new ArrayList<>(original.size());

        for (YamlNode<?> child : sectionNode.value().values()) {
            if (!shouldDump(child)) {
                continue;
            }
            prepareDumpNode(child, restorers);
            filtered.add(new NodeTuple(child.internalKeyNode(), child.internalValueNode()));
        }

        Node firstKeyNode = filtered.isEmpty() ? null : filtered.get(0).getKeyNode();
        List<CommentLine> firstKeyBlockComments = firstKeyNode == null ? null : firstKeyNode.getBlockComments();
        mappingNode.setValue(filtered);
        restorers.add(() -> {
            mappingNode.setValue(original);
            // 序列化器会清空 Mapping 首个 key 的 block comments, 保存结束后恢复 AST
            if (firstKeyNode != null) {
                firstKeyNode.setBlockComments(firstKeyBlockComments);
            }
        });
    }

    private static void prepareSequenceNode(net.momirealms.sparrow.yaml.node.SequenceNode sequenceNode, List<Runnable> restorers) {
        for (YamlNode<?> child : sequenceNode.value()) {
            prepareDumpNode(child, restorers);
        }
    }

    private static boolean shouldDump(@Nullable YamlNode<?> node) {
        return node != null && node.value() != null;
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    private static final class WriterStreamDataWriter implements StreamDataWriter {
        private final Writer writer;

        private WriterStreamDataWriter(Writer writer) {
            this.writer = writer;
        }

        @Override
        public void flush() {
            try {
                this.writer.flush();
            } catch (IOException exception) {
                throw new WriterIOException(exception);
            }
        }

        @Override
        public void write(String str) {
            try {
                this.writer.write(str);
            } catch (IOException exception) {
                throw new WriterIOException(exception);
            }
        }

        @Override
        public void write(String str, int off, int len) {
            try {
                this.writer.write(str, off, len);
            } catch (IOException exception) {
                throw new WriterIOException(exception);
            }
        }
    }

    private static final class StringStreamDataWriter implements StreamDataWriter {
        private final StringBuilder content = new StringBuilder();

        @Override
        public void write(String str) {
            this.content.append(str);
        }

        @Override
        public void write(String str, int off, int len) {
            this.content.append(str, off, off + len);
        }

        private String content() {
            return this.content.toString();
        }
    }

    private static final class WriterIOException extends RuntimeException {
        private final IOException ioException;

        private WriterIOException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }

        private IOException ioException() {
            return this.ioException;
        }
    }
}
