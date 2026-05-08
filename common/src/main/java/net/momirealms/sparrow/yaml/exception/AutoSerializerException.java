package net.momirealms.sparrow.yaml.exception;

public class AutoSerializerException extends RuntimeException {
    public AutoSerializerException(String message) {
        super(message);
    }

    public AutoSerializerException(String message, Throwable cause) {
        super(message, cause);
    }
}
