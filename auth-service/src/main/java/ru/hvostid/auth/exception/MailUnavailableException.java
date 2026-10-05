package ru.hvostid.auth.exception;

import org.springframework.http.HttpStatus;
import ru.hvostid.common.exception.BusinessException;

public class MailUnavailableException extends BusinessException {
    public MailUnavailableException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                "urn:hvostid:mail-unavailable",
                "Email delivery unavailable",
                "Account email delivery is temporarily unavailable. Try again later.");
    }
}
