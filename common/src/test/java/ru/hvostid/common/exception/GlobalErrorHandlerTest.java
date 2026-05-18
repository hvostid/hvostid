package ru.hvostid.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import ru.hvostid.common.dto.ProblemDetails;
import ru.hvostid.common.web.RequestIdMdcFilter;

class GlobalErrorHandlerTest {
    private final GlobalErrorHandler handler = new GlobalErrorHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/listings/123");
        MDC.put(RequestIdMdcFilter.MDC_KEY, "trace-abc");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void businessException_mapsToConfiguredStatus_andCarriesTraceIdAndProblemContentType() {
        ResponseEntity<ProblemDetails> response = handler.handleBusiness(
                new NotFoundException("Listing not found", "Listing 123 does not exist"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetails body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.title()).isEqualTo("Listing not found");
        assertThat(body.detail()).isEqualTo("Listing 123 does not exist");
        assertThat(body.type()).isEqualTo(NotFoundException.TYPE);
        assertThat(body.instance()).isEqualTo("/api/v1/listings/123");
        assertThat(body.traceId()).isEqualTo("trace-abc");
        assertThat(body.errors()).isNull();
    }

    @Test
    void constraintViolation_returns400_withErrorsArray() {
        record Payload(
                @NotBlank(message = "must not be blank") String title) {}
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Payload>> violations = validator.validate(new Payload(""));

        ResponseEntity<ProblemDetails> response =
                handler.handleConstraintViolation(new ConstraintViolationException(violations), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetails body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.title()).isEqualTo("Validation failed");
        assertThat(body.errors()).hasSize(1);
        assertThat(body.errors().getFirst().field()).isEqualTo("title");
        assertThat(body.errors().getFirst().message()).isEqualTo("must not be blank");
    }

    @Test
    void accessDenied_returns403_problem() {
        ResponseEntity<ProblemDetails> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ProblemDetails body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.type()).isEqualTo(ForbiddenException.TYPE);
        // Detail is intentionally generic, never leaks the underlying exception detail to the client.
        assertThat(body.detail()).contains("permission");
    }

    @Test
    void illegalArgument_returns400() {
        ResponseEntity<ProblemDetails> response =
                handler.handleIllegalArgument(new IllegalArgumentException("bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetails body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.detail()).isEqualTo("bad input");
        assertThat(body.type()).isEqualTo(ValidationException.TYPE);
    }

    @Test
    void genericException_returns500_andDoesNotLeakStackTrace() {
        ResponseEntity<ProblemDetails> response =
                handler.handleGeneric(new RuntimeException("with secret stack"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetails body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.title()).isEqualTo("Internal server error");
        assertThat(body.detail()).doesNotContain("secret stack");
        assertThat(body.traceId()).isEqualTo("trace-abc");
    }

    @Test
    void traceId_isNullWhenMdcEmpty() {
        MDC.clear();
        ResponseEntity<ProblemDetails> response = handler.handleBusiness(new ConflictException("dup"), request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().traceId()).isNull();
    }
}
