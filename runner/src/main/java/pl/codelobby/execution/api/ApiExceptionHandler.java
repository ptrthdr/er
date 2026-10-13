package pl.codelobby.execution.api;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.codelobby.execution.service.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException e) {
        String code=switch(e.getStatusCode().value()) {
            case 404->"JOB_NOT_FOUND";case 409->"SUBMISSION_ID_CONFLICT";
            case 413->"PAYLOAD_TOO_LARGE";case 429->"QUEUE_CAPACITY_EXCEEDED";default->"INVALID_REQUEST";
        };
        return ResponseEntity.status(e.getStatusCode()).body(new ApiError(code,
                e.getReason()==null?code:e.getReason(),e.getStatusCode().value()==429));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST","Nieprawidłowa struktura żądania",false));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST","Nieprawidłowy JSON lub nieobsługiwane pole",false));
    }
    @ExceptionHandler(InvalidEvaluationException.class)
    public ResponseEntity<ApiError> handleInvalidEvaluation(InvalidEvaluationException e) {
        return ResponseEntity.badRequest().body(new ApiError(e instanceof InvalidSourceFilesException?
                "INVALID_SOURCE_FILES":"INVALID_REQUEST",e.getMessage(),false));
    }
    @ExceptionHandler(ExecutionEngineUnavailableException.class)
    public ResponseEntity<ApiError> handleEngineUnavailable(ExecutionEngineUnavailableException e) {
        // Never forward upstream response bodies or student-controlled diagnostics.
        return ResponseEntity.status(503).body(new ApiError("EXECUTION_UNAVAILABLE","Runner lub sandbox niedostępny; spróbuj później",true));
    }
}
