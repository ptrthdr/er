package pl.codelobby.execution.api;

import java.util.Map;
import pl.codelobby.execution.service.ExecutionEngineUnavailableException;
import pl.codelobby.execution.service.InvalidEvaluationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(org.springframework.web.server.ResponseStatusException e) {
        String code = switch (e.getStatusCode().value()) {
            case 404 -> "JOB_NOT_FOUND";
            case 409 -> "SUBMISSION_ID_CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 429 -> "QUEUE_CAPACITY_EXCEEDED";
            default -> "REQUEST_REJECTED";
        };
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", code,
                "message", e.getReason() == null ? code : e.getReason(),
                "retryable", e.getStatusCode().value() == 429));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(MethodArgumentNotValidException exception) {
        String field = exception.getBindingResult().getFieldError() == null
                ? "request" : exception.getBindingResult().getFieldError().getField();
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_REQUEST", "message", "Nieprawidłowe pole: " + field));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMalformedJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_JSON", "message", "Nieprawidłowy format żądania"));
    }

    @ExceptionHandler(InvalidEvaluationException.class)
    public ResponseEntity<Map<String, String>> handleInvalidEvaluation(InvalidEvaluationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_EVALUATION", "message", exception.getMessage()));
    }

    @ExceptionHandler(ExecutionEngineUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleEngineUnavailable(ExecutionEngineUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "EXECUTION_ERROR", "message", exception.getMessage()));
    }
}
