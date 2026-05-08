package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.version.VersionMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 描述一次版本迁移区间内需要执行的补丁集合.
 */
public record VersionPatch(
        VersionMatcher predicate,
        List<Patch> orderedPatches
) {

    public VersionPatch {
        orderedPatches = orderedPatches == null ? List.of() : orderedPatches.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public static Builder builder(VersionMatcher predicate) {
        return new Builder(predicate);
    }

    public static class Builder {
        private final VersionMatcher predicate;
        private final List<Patch> patches = new ArrayList<>();

        public Builder(VersionMatcher predicate) {
            this.predicate = predicate;
        }

        /**
         * 添加一个通用补丁实例.
         */
        public Builder patch(Patch patch) {
            this.patches.add(Objects.requireNonNull(patch, "patch"));
            return this;
        }

        /**
         * 添加一个节点迁移规则.
         */
        public Builder relocate(Route from, Route to) {
            return patch(new RelocateRule(from, to));
        }

        /**
         * 添加一个节点转换规则.
         */
        public Builder convert(Route route, Function<YamlNode<?>, Object> converter) {
            return patch(new ConverterRule(route, converter));
        }

        /**
         * 添加一个固定默认值规则.
         */
        public Builder defaultValue(Route route, Object defaultValue) {
            return patch(new DefaultValueRule(route, defaultValue));
        }

        /**
         * 添加一个动态默认值规则.
         */
        public Builder defaultValue(Route route, Function<YamlDocument, Object> provider) {
            return patch(new DefaultValueRule(route, provider));
        }

        /**
         * 添加双文档校验规则.
         */
        public Builder validate(BiConsumer<YamlDocument, YamlDocument> validator) {
            return patch(new ValidationRule(validator));
        }

        /**
         * 添加仅针对本地文档的校验规则.
         */
        public Builder validate(Consumer<YamlDocument> validator) {
            return patch(new ValidationRule(validator));
        }

        /**
         * 添加基于 BiPredicate 的校验规则.
         */
        public Builder validate(BiPredicate<YamlDocument, YamlDocument> predicate, String errorMessage) {
            return patch(new ValidationRule(predicate, errorMessage));
        }

        /**
         * 生成最终的 VersionPatch.
         */
        public VersionPatch build() {
            return new VersionPatch(this.predicate, List.copyOf(this.patches));
        }
    }
}
