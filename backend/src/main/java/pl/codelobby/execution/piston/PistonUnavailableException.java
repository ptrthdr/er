package pl.codelobby.execution.piston;

import org.springframework.web.client.RestClientException;

public class PistonUnavailableException extends RestClientException {
    public PistonUnavailableException(String message) {
        super(message);
    }

    public PistonUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
