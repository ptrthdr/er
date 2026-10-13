package pl.codelobby.execution.service;

public class InvalidEvaluationException extends RuntimeException {
    public InvalidEvaluationException(String message) {
        super(message);
    }
}
