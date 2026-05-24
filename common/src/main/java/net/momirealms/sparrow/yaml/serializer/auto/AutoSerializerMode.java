package net.momirealms.sparrow.yaml.serializer.auto;

/**
 * Selects the implementation used when a serializer is generated automatically.
 */
public enum AutoSerializerMode {
    /**
     * Use the ASM implementation when it is available at runtime, otherwise use reflection.
     */
    ADAPTIVE,

    /**
     * Require the ASM implementation. Registration fails if ASM is not available.
     */
    ASM,

    /**
     * Use the reflection implementation.
     */
    REFLECTION
}
