package ru.hvostid.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;

@SpringBootTest
class AuthServiceApplicationTest extends AbstractPostgresContainerTest {
    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts successfully
    }
}
