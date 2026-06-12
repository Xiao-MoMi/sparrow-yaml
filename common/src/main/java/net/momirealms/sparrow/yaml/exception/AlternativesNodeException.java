package net.momirealms.sparrow.yaml.exception;

import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 表示 alternatives 的同形态候选都无法解码当前 YAML 节点.
 */
public final class AlternativesNodeException extends InvalidNodeException {
    private final List<Failure> failures; // 候选的原始失败
    private final String detail; // 面向日志的简短摘要

    public AlternativesNodeException(
            YamlNode<?> node,
            Class<?> targetType,
            List<Failure> failures,
            String detail
    ) {
        super(node, targetType);
        this.failures = List.copyOf(failures);
        this.detail = Objects.requireNonNull(detail, "detail");
        for (Failure failure : this.failures) {
            addSuppressed(failure.exception());
        }
    }

    /**
     * 返回候选的原始失败.
     */
    public List<Failure> failures() {
        return failures;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ". " + detail;
    }

    public record Failure(NodeParsingException exception) {
        public Failure {
            Objects.requireNonNull(exception, "exception");
        }

        /**
         * 返回原始异常指向的 YAML 路径.
         */
        @Nullable
        public Route path() {
            if (exception instanceof MissingNodeException missing) {
                return missing.path();
            }
            if (exception instanceof InvalidNodeException invalid) {
                return invalid.path();
            }
            return null;
        }

        /**
         * 返回原始异常记录的实际 Java 类型.
         */
        @Nullable
        public Class<?> actualType() {
            if (exception instanceof InvalidNodeException invalid) {
                return invalid.actualType();
            }
            return null;
        }

        /**
         * 返回原始异常记录的目标 Java 类型.
         */
        @Nullable
        public Class<?> targetType() {
            if (exception instanceof MissingNodeException missing) {
                return missing.targetType();
            }
            if (exception instanceof InvalidNodeException invalid) {
                return invalid.targetType();
            }
            return null;
        }

        /**
         * 返回缺失字段名或序列下标.
         */
        @Nullable
        public Object missingKey() {
            if (exception instanceof MissingNodeException missing) {
                return missing.key();
            }
            return null;
        }

        public String message() {
            return exception.getMessage();
        }
    }
}
