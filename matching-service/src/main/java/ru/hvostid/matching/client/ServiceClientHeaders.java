package ru.hvostid.matching.client;

import org.springframework.http.HttpHeaders;
import ru.hvostid.common.http.SecurityHeaders;

public final class ServiceClientHeaders {
    private ServiceClientHeaders() {}

    public static HttpHeaders withRequestId(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        if (requestId != null && !requestId.isBlank()) {
            headers.set(SecurityHeaders.REQUEST_ID, requestId);
        }
        return headers;
    }

    public static HttpHeaders withRequestIdAndUserId(String requestId, long userId) {
        HttpHeaders headers = withRequestId(requestId);
        headers.set(SecurityHeaders.USER_ID, String.valueOf(userId));
        return headers;
    }
}
