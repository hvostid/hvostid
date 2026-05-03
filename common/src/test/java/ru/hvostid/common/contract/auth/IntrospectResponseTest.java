package ru.hvostid.common.contract.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.hvostid.common.security.UserRole;

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
        var response = IntrospectResponse.active(42L, List.of(UserRole.BUYER.value(), UserRole.SELLER.value()));
        assertTrue(response.active());
        assertEquals(42L, response.userId());
        assertEquals(List.of(UserRole.BUYER.value(), UserRole.SELLER.value()), response.roles());
    }

    @Test
    void constructor_shouldPreserveAllFields() {
        var response = new IntrospectResponse(true, 1L, List.of(UserRole.ADMIN.value()));
        assertTrue(response.active());
        assertEquals(1L, response.userId());
        assertEquals(List.of(UserRole.ADMIN.value()), response.roles());
    }

    @Test
    void equalResponses_shouldBeEqual() {
        var a = IntrospectResponse.active(1L, List.of(UserRole.BUYER.value()));
        var b = IntrospectResponse.active(1L, List.of(UserRole.BUYER.value()));
        assertEquals(a, b);
    }
}
