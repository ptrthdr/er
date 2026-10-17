package pl.codelobby.execution.service;

import org.springframework.http.HttpStatus;
import pl.codelobby.execution.common.error.ApiException;

public class InvalidEvaluationException extends ApiException {
    public InvalidEvaluationException(String message) {
        this("INVALID_REQUEST", message);
    }

    protected InvalidEvaluationException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message, false);
    }
}
