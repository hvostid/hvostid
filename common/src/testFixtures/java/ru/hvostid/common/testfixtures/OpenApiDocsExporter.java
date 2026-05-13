package ru.hvostid.common.testfixtures;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared helper for OpenAPI export integration tests. Each backend service has
 * a small test that calls {@link #export} to write its generated
 * {@code /v3/api-docs} into {@code build/openapi/<service>.json}; CI then
 * uploads those JSON files as build artifacts (acceptance criterion of T40).
 */
public final class OpenApiDocsExporter {
    public static final String DOCS_PATH = "/v3/api-docs";
    public static final Path OUTPUT_DIR = Path.of("build", "openapi");

    private OpenApiDocsExporter() {}

    /**
     * Fetches the OpenAPI JSON via MockMvc and writes it to
     * {@code build/openapi/<service>.json} relative to the module root.
     * The file is also returned so the caller can run extra assertions.
     */
    public static Path export(MockMvc mockMvc, String service) throws Exception {
        MvcResult result =
                mockMvc.perform(get(DOCS_PATH)).andExpect(status().isOk()).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        Files.createDirectories(OUTPUT_DIR);
        Path output = OUTPUT_DIR.resolve(service + ".json");
        Files.write(output, body);
        return output;
    }
}
