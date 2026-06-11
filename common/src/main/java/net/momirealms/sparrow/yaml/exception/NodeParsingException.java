package net.momirealms.sparrow.yaml.exception;

public class NodeParsingException extends RuntimeException {

    public NodeParsingException(String message) {
        super(message);
    }

    public NodeParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
