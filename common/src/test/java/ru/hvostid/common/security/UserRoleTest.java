package ru.hvostid.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserRoleTest {
    @Test
    void value_returnsUppercaseRoleName() {
        assertEquals("SELLER", UserRole.SELLER.value());
    }

    @Test
    void authority_returnsSpringSecurityAuthority() {
        assertEquals("ROLE_SELLER", UserRole.SELLER.authority());
    }

    @Test
    void fromValue_acceptsUppercaseValue() {
        assertEquals(UserRole.SELLER, UserRole.fromValue("SELLER"));
    }

    @Test
    void fromValue_rejectsLowercaseValue() {
        assertThrows(IllegalArgumentException.class, () -> UserRole.fromValue("seller"));
    }

    @Test
    void fromValue_rejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> UserRole.fromValue("owner"));
    }
}
