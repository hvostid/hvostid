package ru.hvostid.listing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.common.testfixtures.OpenApiDocsExporter;

/**
 * Boots the application context, calls {@code /v3/api-docs}, and writes the
 * generated spec to {@code build/openapi/listing-service.json}. CI picks the
 * file up as an artifact (T40 acceptance criterion).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest extends AbstractPostgresContainerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsOpenApiJson() throws Exception {
        Path output = OpenApiDocsExporter.export(mockMvc, "listing-service");
        assertTrue(Files.size(output) > 0, "OpenAPI JSON should not be empty");
    }
}
