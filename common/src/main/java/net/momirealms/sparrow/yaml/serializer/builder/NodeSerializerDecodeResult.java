package net.momirealms.sparrow.yaml.serializer.builder;

import java.util.function.Function;

/**
 * builder 内部使用的解码结果, 用 success 区分 null 值和失败.
 */
record NodeSerializerDecodeResult(boolean success, Object value) {

    static NodeSerializerDecodeResult success(Object value) {
        return new NodeSerializerDecodeResult(true, value);
    }

    static NodeSerializerDecodeResult failed() {
        return new NodeSerializerDecodeResult(false, null);
    }

    static <A> NodeSerializerDecodeResult fallback(
            Function<? super RuntimeException, ? extends A> failureHandler,
            RuntimeException failure
    ) {
        if (failureHandler == null) {
            throw failure;
        }
        try {
            A value = failureHandler.apply(failure);
            return value == null ? NodeSerializerDecodeResult.failed() : NodeSerializerDecodeResult.success(value);
        } catch (Exception e) {
            return NodeSerializerDecodeResult.failed();
        }
    }
}
