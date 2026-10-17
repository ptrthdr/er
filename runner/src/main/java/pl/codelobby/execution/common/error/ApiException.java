package pl.codelobby.execution.common.error;

import org.springframework.http.HttpStatus;

/** Expected API failure. The message must be safe to return to the caller. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    public ApiException(HttpStatus status, String code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
