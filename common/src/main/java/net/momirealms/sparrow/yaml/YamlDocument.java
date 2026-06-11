package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class YamlDocument extends SectionNode {
    private final SparrowYaml sparrowYaml;

    public YamlDocument(SparrowYaml sparrowYaml, InputStream inputStream) throws IOException {
        super(null, null, (MappingNode) sparrowYaml.standardRepresenter().represent(new java.util.LinkedHashMap<>()), null, new ExtendedConstructor(sparrowYaml.loadSettings()));
        this.sparrowYaml = sparrowYaml;
        this.reload(inputStream);
    }

    /**
     * 获取生产该文档对象的 SparrowYaml 对象;
     * @return SparrowYaml;
     */
    public SparrowYaml sparrowYaml() {
        return sparrowYaml;
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
     * 加载和读取 Yaml 文件;
     */
    public void reload(InputStream inputStream) throws IOException {
        LoadSettings loadSettings = this.sparrowYaml().loadSettings();
        Parser parser = new ParserImpl(loadSettings, new StreamReader(loadSettings, new YamlUnicodeReader(Objects.requireNonNull(inputStream, "Input stream cannot be null!"))));
        Composer composer = new Composer(loadSettings, parser);
        ExtendedConstructor constructor = new ExtendedConstructor(loadSettings);

        if (composer.hasNext()) {
            Node node = composer.next();
            // 检查合法性
            if (composer.hasNext()) {
                throw new InvalidObjectException("暂不支持多文档解析!");
            }
            if (!(node instanceof MappingNode mappingNode)) {
                throw new IllegalArgumentException(String.format("根节点对象不是MappingNode！解析节点: %s", node.toString()));
            }
            // 将解析完的节点构造成 Java 对象.
            constructor.constructSingleDocument(Optional.of(node));
            this.valueNode = mappingNode;
            this.value().clear();
            // 构造节点
            super.init(null, mappingNode, constructor);
            // 清理构造器缓存.
            constructor.clear();
        }
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
     * 将当前 YamlDocument 保存到 Writer 中;
     * @param writer 目标 Writer
     */
    public void save(Writer writer) throws IOException {
        StreamDataWriter streamDataWriter = new StreamDataWriter() {
            @Override
            public void flush() {
                try {
                    writer.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void write(String str) {
                try {
                    writer.write(str);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void write(String str, int off, int len) {
                try {
                    writer.write(str, off, len);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

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

        mappingNode.setValue(filtered);
        restorers.add(() -> mappingNode.setValue(original));
    }

    private static void prepareSequenceNode(net.momirealms.sparrow.yaml.node.SequenceNode sequenceNode, List<Runnable> restorers) {
        org.snakeyaml.engine.v2.nodes.SequenceNode internalNode =
                (org.snakeyaml.engine.v2.nodes.SequenceNode) sequenceNode.internalValueNode();
        List<Node> original = new ArrayList<>(internalNode.getValue());
        List<Node> filtered = new ArrayList<>(original.size());

        for (YamlNode<?> child : sequenceNode.value()) {
            if (!shouldDump(child)) {
                continue;
            }
            prepareDumpNode(child, restorers);
            filtered.add(child.internalValueNode());
        }

        internalNode.getValue().clear();
        internalNode.getValue().addAll(filtered);
        restorers.add(() -> {
            internalNode.getValue().clear();
            internalNode.getValue().addAll(original);
        });
    }

    private static boolean shouldDump(@Nullable YamlNode<?> node) {
        return node != null && node.value() != null;
    }

    @Override
    public boolean isRoot() {
        return true;
    }
}
