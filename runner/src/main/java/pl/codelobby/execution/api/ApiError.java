package pl.codelobby.execution.api;

import pl.codelobby.execution.common.error.ApiException;

public record ApiError(String code, String message, boolean retryable) {

    public static ApiError from(ApiException exception) {
        return new ApiError(exception.getCode(), exception.getMessage(), exception.isRetryable());
    }

    public static ApiError executionUnavailable() {
        return new ApiError("EXECUTION_UNAVAILABLE", "Runner lub sandbox niedostępny; spróbuj później", true);
    }

    public static ApiError internalServerError() {
        return new ApiError("INTERNAL_SERVER_ERROR", "Wystąpił nieoczekiwany błąd serwera", false);
    }
}
