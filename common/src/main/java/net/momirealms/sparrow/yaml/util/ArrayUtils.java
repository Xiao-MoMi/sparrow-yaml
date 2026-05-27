package net.momirealms.sparrow.yaml.util;

import java.util.Arrays;

public final class ArrayUtils {
    private ArrayUtils() {}

    public static <T> T[] mergeArrays(T[] arr1, T[] arr2) {
        T[] result = Arrays.copyOf(arr1, arr1.length + arr2.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }

}
