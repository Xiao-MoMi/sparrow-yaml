package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.serializer.impl.*;

public final class NodeSerializers {
    private NodeSerializers() {}

    public static final NodeSerializer<Object> OBJECT_SERIALIZER = new ObjectSerializer();
    public static final NodeSerializer<Integer> INT_SERIALIZER = new IntSerializer();
    public static final NodeSerializer<Float> FLOAT_SERIALIZER = new FloatSerializer();
    public static final NodeSerializer<Double> DOUBLE_SERIALIZER = new DoubleSerializer();
    public static final NodeSerializer<Long> LONG_SERIALIZER = new LongSerializer();
    public static final NodeSerializer<Boolean> BOOLEAN_SERIALIZER = new BooleanSerializer();
    public static final NodeSerializer<String> STRING_SERIALIZER = new StringSerializer();
}