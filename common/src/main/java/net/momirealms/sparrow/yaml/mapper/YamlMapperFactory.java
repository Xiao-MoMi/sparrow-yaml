package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YamlMapper 工厂, 运行期为 @Configuration 类型创建文档映射器.
 */
public class YamlMapperFactory {
    private final SparrowYaml sparrowYaml;
    private final YamlUpgradePipeline upgradePipeline;
    private static final Map<Class<?>, ConfigDocumentMapper<?>> MAPPER_CACHE = new ConcurrentHashMap<>();

    private YamlMapperFactory(SparrowYaml sparrowYaml, YamlUpgradePipeline upgradePipeline) {
        this.sparrowYaml = sparrowYaml != null ? sparrowYaml : SparrowYaml.builder().build();
        this.upgradePipeline = upgradePipeline;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 为指定的 @Configuration 配置类创建一个 YamlMapper.
     *
     * @param clazz 配置类的 Class 对象
     * @param <T>   类型参数
     * @return YamlMapper 实例
     */
    public <T> YamlMapper<T> create(Class<T> clazz) {
        ConfigDocumentMapper<T> mapper = this.getMapper(clazz);
        return new YamlMapper<>(clazz, sparrowYaml, mapper, upgradePipeline);
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigDocumentMapper<T> getMapper(Class<T> clazz) {
        return (ConfigDocumentMapper<T>) MAPPER_CACHE.computeIfAbsent(clazz, RuntimeConfigDocumentMapper::new);
    }

    public static class Builder {
        private SparrowYaml sparrowYaml;
        private YamlUpgradePipeline upgradePipeline;

        public Builder sparrowYaml(SparrowYaml sparrowYaml) {
            this.sparrowYaml = sparrowYaml;
            return this;
        }

        public Builder upgradePipeline(YamlUpgradePipeline upgradePipeline) {
            this.upgradePipeline = upgradePipeline;
            return this;
        }

        public YamlMapperFactory build() {
            return new YamlMapperFactory(sparrowYaml, upgradePipeline);
        }
    }
}
