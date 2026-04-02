package ru.hvostid.common.contract.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntrospectResponseTest {
    @Test
    void inactive_shouldReturnFalseActiveWithNulls() {
        var response = IntrospectResponse.inactive();
        assertFalse(response.active());
        assertNull(response.userId());
        assertNull(response.roles());
    }

    @Test
    void active_shouldReturnTrueActiveWithUserData() {
        var response = IntrospectResponse.active(42L, List.of("buyer", "seller"));
        assertTrue(response.active());
        assertEquals(42L, response.userId());
        assertEquals(List.of("buyer", "seller"), response.roles());
    }

    @Test
    void constructor_shouldPreserveAllFields() {
        var response = new IntrospectResponse(true, 1L, List.of("admin"));
        assertTrue(response.active());
        assertEquals(1L, response.userId());
        assertEquals(List.of("admin"), response.roles());
    }

    @Test
    void equalResponses_shouldBeEqual() {
        var a = IntrospectResponse.active(1L, List.of("buyer"));
        var b = IntrospectResponse.active(1L, List.of("buyer"));
        assertEquals(a, b);
    }
}
