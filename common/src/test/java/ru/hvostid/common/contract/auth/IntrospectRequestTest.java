package ru.hvostid.common.contract.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IntrospectRequestTest {
    @Test
    void shouldCreateRequestWithToken() {
        var request = new IntrospectRequest("abc123");
        assertEquals("abc123", request.token());
    }

    @Test
    void shouldPreserveTokenValue() {
        var request = new IntrospectRequest("opaque-token-xyz");
        assertEquals("opaque-token-xyz", request.token());
    }

    @Test
    void equalRequests_shouldBeEqual() {
        var a = new IntrospectRequest("same");
        var b = new IntrospectRequest("same");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentRequests_shouldNotBeEqual() {
        var a = new IntrospectRequest("token-a");
        var b = new IntrospectRequest("token-b");
        assertNotEquals(a, b);
    }
}
