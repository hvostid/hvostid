package ru.hvostid.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ErrorResponseTest {
    @Test
    void shouldCreateErrorResponseWithAllFields() {
        var response = new ErrorResponse(404, "Not Found", "Resource not found", "/api/v1/test");

        assertEquals(404, response.status());
        assertEquals("Not Found", response.error());
        assertEquals("Resource not found", response.message());
        assertEquals("/api/v1/test", response.path());
        assertNotNull(response.timestamp());
    }
}
