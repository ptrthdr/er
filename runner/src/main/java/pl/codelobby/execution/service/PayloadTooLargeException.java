package pl.codelobby.execution.service;

import org.springframework.http.HttpStatus;
import pl.codelobby.execution.common.error.ApiException;

public class PayloadTooLargeException extends ApiException {
    public PayloadTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "Treść żądania przekracza dozwolony rozmiar", false);
    }
}
