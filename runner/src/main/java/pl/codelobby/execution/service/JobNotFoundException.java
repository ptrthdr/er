package pl.codelobby.execution.service;

import org.springframework.http.HttpStatus;
import pl.codelobby.execution.common.error.ApiException;

public class JobNotFoundException extends ApiException {
    public JobNotFoundException() {
        super(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND",
                "Nie znaleziono zgłoszenia lub wynik wygasł", false);
    }
}
