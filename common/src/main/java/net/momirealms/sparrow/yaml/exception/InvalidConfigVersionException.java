package net.momirealms.sparrow.yaml.exception;

public class InvalidConfigVersionException extends RuntimeException {

    public InvalidConfigVersionException(String message) {
        super(message);
    }

    public InvalidConfigVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
