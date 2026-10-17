package pl.codelobby.execution.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.codelobby.execution.service.ExecutionEngineUnavailableException;
import pl.codelobby.execution.service.InvalidEvaluationException;
import pl.codelobby.execution.service.InvalidSourceFilesException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .sorted()
                .limit(10)
                .collect(Collectors.joining(", "));
        String message = fields.isEmpty()
                ? "Nieprawidłowa struktura żądania"
                : "Nieprawidłowe pola żądania: " + fields;

        return handleExceptionInternal(exception,
                new ApiError("INVALID_REQUEST", message, false), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return handleExceptionInternal(exception,
                new ApiError("INVALID_REQUEST", "Nieprawidłowy JSON lub nieobsługiwane pole", false),
                headers, status, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (status.is5xxServerError()) {
            log.error("Internal execution API error", exception);
        }
        ApiError error = body instanceof ApiError apiError
                ? apiError : errorForStatus(status, exception);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        responseHeaders.setCacheControl("no-store");

        return super.handleExceptionInternal(exception, error, responseHeaders, status, request);
    }

    @ExceptionHandler(InvalidEvaluationException.class)
    public ResponseEntity<ApiError> handleInvalidEvaluation(InvalidEvaluationException exception) {
        String code = exception instanceof InvalidSourceFilesException
                ? "INVALID_SOURCE_FILES" : "INVALID_REQUEST";
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(code, exception.getMessage(), false));
    }

    @ExceptionHandler(ExecutionEngineUnavailableException.class)
    public ResponseEntity<ApiError> handleEngineUnavailable(ExecutionEngineUnavailableException exception) {
        log.warn("Execution engine unavailable", exception);
        return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("EXECUTION_UNAVAILABLE",
                        "Runner lub sandbox niedostępny; spróbuj później", true));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unexpected execution API error", exception);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("INTERNAL_SERVER_ERROR", "Wystąpił nieoczekiwany błąd serwera", false));
    }

    private static ApiError errorForStatus(HttpStatusCode status, Exception exception) {
        return switch (status.value()) {
            case 400 -> new ApiError("INVALID_REQUEST", "Nieprawidłowa struktura żądania", false);
            case 401 -> new ApiError("UNAUTHORIZED", "Brak lub nieprawidłowy klucz API", false);
            case 403 -> new ApiError("FORBIDDEN", "Brak dostępu do zasobu", false);
            case 404 -> exception instanceof ResponseStatusException
                    ? new ApiError("JOB_NOT_FOUND", "Nie znaleziono zgłoszenia lub wynik wygasł", false)
                    : new ApiError("RESOURCE_NOT_FOUND", "Nie znaleziono endpointu", false);
            case 405 -> new ApiError("METHOD_NOT_ALLOWED", "Metoda HTTP nie jest obsługiwana dla tego adresu", false);
            case 406 -> new ApiError("NOT_ACCEPTABLE", "API zwraca odpowiedzi w formacie JSON", false);
            case 409 -> new ApiError("SUBMISSION_ID_CONFLICT", "submissionId jest już użyte dla innych danych", false);
            case 413 -> new ApiError("PAYLOAD_TOO_LARGE", "Treść żądania przekracza dozwolony rozmiar", false);
            case 415 -> new ApiError("UNSUPPORTED_MEDIA_TYPE", "Nieobsługiwany Content-Type; użyj application/json", false);
            case 429 -> new ApiError("QUEUE_CAPACITY_EXCEEDED", "Kolejka lub magazyn wyników jest pełny; spróbuj później", true);
            case 503 -> new ApiError("EXECUTION_UNAVAILABLE", "Runner lub sandbox niedostępny; spróbuj później", true);
            default -> status.is5xxServerError()
                    ? new ApiError("INTERNAL_SERVER_ERROR", "Wystąpił nieoczekiwany błąd serwera", false)
                    : new ApiError("INVALID_REQUEST", "Nieprawidłowe żądanie", false);
        };
    }
}
