package pl.codelobby.execution.api;
public record ApiError(String code, String message, boolean retryable) {}
