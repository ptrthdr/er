package pl.codelobby.execution.service;

public class InvalidSourceFilesException extends InvalidEvaluationException {
    public InvalidSourceFilesException(String message) {
        super("INVALID_SOURCE_FILES", message);
    }
}
