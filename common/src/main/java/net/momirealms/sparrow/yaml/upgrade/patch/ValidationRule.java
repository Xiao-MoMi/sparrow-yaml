package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.PatchValidationException;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * 在升级流程末段执行校验的补丁规则.
 * 校验失败时会抛出 {@link PatchValidationException}.
 */
public class ValidationRule implements Patch {

    /** 校验逻辑, 可同时访问模板文档和本地文档. */
    private final BiConsumer<YamlDocument, YamlDocument> validator;

    /**
     * 创建一个双文档校验规则.
     */
    public ValidationRule(BiConsumer<YamlDocument, YamlDocument> validator) {
        this.validator = validator;
    }

    /**
     * 创建一个仅关注本地文档的校验规则.
     */
    public ValidationRule(Consumer<YamlDocument> validator) {
        this((def, local) -> validator.accept(local));
    }

    /**
     * 通过布尔谓词构造校验规则, 谓词返回 false 时抛出指定异常信息.
     */
    public ValidationRule(BiPredicate<YamlDocument, YamlDocument> predicate, String errorMessage) {
        this((local, def) -> {
            if (!predicate.test(local, def)) {
                throw new PatchValidationException(errorMessage);
            }
        });
    }

    /**
     * 执行校验逻辑, 并将非预期异常包装为 PatchValidationException.
     */
    @Override
    public YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context) {
        try {
            validator.accept(defDoc, localDoc);
        } catch (PatchValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new PatchValidationException("Validation failed with an unexpected exception", e);
        }
        return localDoc;
    }

    /**
     * 校验规则在所有改写操作之后执行.
     */
    @Override
    public int getOrder() {
        return 50;
    }
}
