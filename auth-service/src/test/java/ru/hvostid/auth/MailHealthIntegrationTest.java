package ru.hvostid.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;

class MailHealthIntegrationTest {
    private static void assertSmtpOutageDoesNotFailHealth(JavaMailSenderImpl sender, MockMvc mvc) throws Exception {
        // Check the real sender cannot connect, without attempting to send any email.
        assertThrows(MessagingException.class, sender::testConnection);
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status").value("UP"))
                .andExpect(jsonPath("components.db.status").value("UP"))
                .andExpect(jsonPath("components.mail").doesNotExist());
    }

    @Nested
    @SpringBootTest(
            properties = {
                "spring.config.location=file:src/main/resources/application.yml",
                "hvostid.auth.mail.enabled=false",
                "spring.mail.host=127.0.0.1",
                "spring.mail.port=1",
                "spring.mail.properties.mail.smtp.connectiontimeout=100",
                "management.endpoint.health.show-details=always"
            })
    @AutoConfigureMockMvc
    class DisabledMail extends AbstractPostgresContainerTest {
        @Autowired
        JavaMailSenderImpl sender;

        @Autowired
        MockMvc mvc;

        @Test
        void disabledMailDoesNotBlockServiceHealth() throws Exception {
            assertSmtpOutageDoesNotFailHealth(sender, mvc);
        }
    }

    @Nested
    @SpringBootTest(
            properties = {
                "spring.config.location=file:src/main/resources/application.yml",
                "hvostid.auth.mail.enabled=true",
                "hvostid.auth.mail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "hvostid.auth.mail.initial-delay=PT1H",
                "spring.mail.host=127.0.0.1",
                "spring.mail.port=1",
                "spring.mail.properties.mail.smtp.connectiontimeout=100",
                "management.endpoint.health.show-details=always"
            })
    @AutoConfigureMockMvc
    class EnabledMail extends AbstractPostgresContainerTest {
        @Autowired
        JavaMailSenderImpl sender;

        @Autowired
        MockMvc mvc;

        @Test
        void smtpOutageDoesNotBlockServiceHealth() throws Exception {
            assertSmtpOutageDoesNotFailHealth(sender, mvc);
        }
    }
}
