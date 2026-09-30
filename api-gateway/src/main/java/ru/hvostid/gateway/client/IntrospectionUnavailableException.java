package ru.hvostid.gateway.client;

public class IntrospectionUnavailableException extends RuntimeException {
    public IntrospectionUnavailableException(Throwable cause) {
        super("Authentication service is temporarily unavailable", cause);
    }
}
