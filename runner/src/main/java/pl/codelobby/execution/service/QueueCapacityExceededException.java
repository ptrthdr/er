package pl.codelobby.execution.service;

import org.springframework.http.HttpStatus;
import pl.codelobby.execution.common.error.ApiException;

public class QueueCapacityExceededException extends ApiException {
    public QueueCapacityExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUEUE_CAPACITY_EXCEEDED",
                "Kolejka lub magazyn wyników jest pełny; spróbuj później", true);
    }
}
