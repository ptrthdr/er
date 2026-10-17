package pl.codelobby.execution.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.codelobby.execution.common.error.ApiException;
import pl.codelobby.execution.service.ExecutionEngineUnavailableException;

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
        ApiError error = body instanceof ApiError apiError ? apiError
                : status.is5xxServerError() ? ApiError.internalServerError()
                : new ApiError("HTTP_ERROR", "Nie można obsłużyć żądania HTTP", false);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        responseHeaders.setCacheControl("no-store");

        return super.handleExceptionInternal(exception, error, responseHeaders, status, request);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return errorResponse(exception.getStatus(), ApiError.from(exception));
    }

    @ExceptionHandler(ExecutionEngineUnavailableException.class)
    public ResponseEntity<ApiError> handleEngineUnavailable(ExecutionEngineUnavailableException exception) {
        log.warn("Execution engine unavailable", exception);
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, ApiError.executionUnavailable());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unexpected execution API error", exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.internalServerError());
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ApiError error = status.is5xxServerError() ? ApiError.internalServerError()
                : new ApiError("INVALID_REQUEST", "Nieprawidłowe parametry żądania", false);
        return handleExceptionInternal(exception, error, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception,
                new ApiError("RESOURCE_NOT_FOUND", "Nie znaleziono endpointu", false), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception,
                new ApiError("RESOURCE_NOT_FOUND", "Nie znaleziono endpointu", false), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception,
                new ApiError("METHOD_NOT_ALLOWED", "Metoda HTTP nie jest obsługiwana dla tego adresu", false),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception,
                new ApiError("NOT_ACCEPTABLE", "API zwraca odpowiedzi w formacie JSON", false),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception,
                new ApiError("UNSUPPORTED_MEDIA_TYPE", "Nieobsługiwany Content-Type; użyj application/json", false),
                headers, status, request);
    }

    private ResponseEntity<ApiError> errorResponse(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).body(error);
    }
}
