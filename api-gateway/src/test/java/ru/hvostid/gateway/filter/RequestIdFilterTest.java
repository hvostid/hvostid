package ru.hvostid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static ru.hvostid.common.http.SecurityHeaders.REQUEST_ID;
import static ru.hvostid.gateway.filter.RequestIdFilter.MDC_REQUEST_ID_KEY;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void shouldGenerateRequestIdWhenNotProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (_, _) -> {
        });

        String responseId = response.getHeader(REQUEST_ID);
        assertNotNull(responseId, "Response must contain X-Request-Id");
        assertDoesNotThrow(() -> UUID.fromString(responseId), "Generated request id must be a valid UUID");
    }

    @Test
    void shouldReuseClientProvidedRequestId() throws ServletException, IOException {
        String clientId = "client-provided-id-12345";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        request.addHeader(REQUEST_ID, clientId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (_, _) -> {
        });

        assertEquals(clientId, response.getHeader(REQUEST_ID), "Response must echo the client-provided X-Request-Id");
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderIsBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        request.addHeader(REQUEST_ID, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (_, _) -> {
        });

        String responseId = response.getHeader(REQUEST_ID);
        assertNotNull(responseId);
        assertDoesNotThrow(() -> UUID.fromString(responseId), "Blank header should trigger UUID generation");
    }

    @Test
    void shouldPropagateRequestIdToDownstreamViaRequestWrapper() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamHeader = new AtomicReference<>();

        // The filter chain simulates reading the header from the (possibly wrapped) request
        FilterChain chain = (req, _) -> downstreamHeader.set(((jakarta.servlet.http.HttpServletRequest) req).getHeader(REQUEST_ID));

        filter.doFilterInternal(request, response, chain);

        assertNotNull(downstreamHeader.get(), "Downstream must receive X-Request-Id via wrapped request");
        assertEquals(response.getHeader(REQUEST_ID), downstreamHeader.get(), "Downstream and response X-Request-Id must match");
    }

    @Test
    void shouldPreserveClientProvidedIdInDownstreamRequest() throws ServletException, IOException {
        String clientId = "my-trace-id-999";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader(REQUEST_ID, clientId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamHeader = new AtomicReference<>();

        FilterChain chain = (req, _) -> downstreamHeader.set(((jakarta.servlet.http.HttpServletRequest) req).getHeader(REQUEST_ID));

        filter.doFilterInternal(request, response, chain);

        assertEquals(clientId, downstreamHeader.get(), "Client-provided id must be forwarded to downstream");
    }

    @Test
    void shouldPutRequestIdIntoMdcDuringFilterExecution() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValue = new AtomicReference<>();

        FilterChain chain = (_, _) -> mdcValue.set(MDC.get(MDC_REQUEST_ID_KEY));

        filter.doFilterInternal(request, response, chain);

        assertNotNull(mdcValue.get(), "MDC must contain requestId during filter chain");
        assertEquals(response.getHeader(REQUEST_ID), mdcValue.get(), "MDC requestId must match the response header");
    }

    @Test
    void shouldCleanMdcAfterFilterExecution() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (_, _) -> {
        });

        assertNull(MDC.get(MDC_REQUEST_ID_KEY), "MDC must be cleaned after the filter completes");
    }

    @Test
    void shouldCleanMdcEvenWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain failingChain = (_, _) -> {
            throw new ServletException("Simulated failure");
        };

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, failingChain));

        assertNull(MDC.get(MDC_REQUEST_ID_KEY), "MDC must be cleaned even on exception");
    }

    @Test
    void shouldSetResponseHeaderBeforeChainExecution() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> headerDuringChain = new AtomicReference<>();

        FilterChain chain = (_, res) -> headerDuringChain.set(((MockHttpServletResponse) res).getHeader(REQUEST_ID));

        filter.doFilterInternal(request, response, chain);

        assertNotNull(headerDuringChain.get(), "Response header must be set before the chain runs");
    }
}
