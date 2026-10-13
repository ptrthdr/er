package pl.codelobby.execution.service;

public class ExecutionEngineUnavailableException extends RuntimeException {
    public ExecutionEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
