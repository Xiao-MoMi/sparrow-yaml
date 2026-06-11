package net.momirealms.sparrow.yaml.serializer;

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
}
