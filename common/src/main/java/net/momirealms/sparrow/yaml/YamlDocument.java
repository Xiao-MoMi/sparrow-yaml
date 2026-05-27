package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.engine.ExtendedConstructor;
import net.momirealms.sparrow.yaml.node.SectionNode;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.io.FileOutputStream;
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
        dump.dumpNode(this.internalValueNode(), streamDataWriter);
    }

    @Override
    public boolean isRoot() {
        return true;
    }
}
