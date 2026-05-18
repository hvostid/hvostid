package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for domain-level exceptions thrown by any service. Subclasses encode the
 * intended HTTP status plus a stable problem-type URI used in the RFC 7807
 * {@code ProblemDetails.type} field.
 *
 * <p>The handler chain in {@code common.exception.GlobalErrorHandler} maps any
 * {@link BusinessException} to the corresponding status code, so service code can
 * throw without binding to a {@link org.springframework.web.bind.annotation.ResponseStatus}.
 */
public abstract class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String type;
    private final String title;

    protected BusinessException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    protected BusinessException(HttpStatus status, String type, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String title() {
        return title;
    }
}
