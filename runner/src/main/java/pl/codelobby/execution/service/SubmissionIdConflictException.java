package pl.codelobby.execution.service;

import org.springframework.http.HttpStatus;
import pl.codelobby.execution.common.error.ApiException;

public class SubmissionIdConflictException extends ApiException {
    public SubmissionIdConflictException() {
        super(HttpStatus.CONFLICT, "SUBMISSION_ID_CONFLICT",
                "submissionId jest już użyte dla innych danych", false);
    }
}
