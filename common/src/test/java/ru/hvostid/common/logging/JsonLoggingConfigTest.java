package ru.hvostid.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Two-layer regression guard for the shared JSON logging contract.
 *
 * <ol>
 *   <li>Encoder behavior is exercised programmatically with {@link LogstashEncoder} configured
 *       the same way as {@code logback-shared.xml}, so the test does not depend on Spring Boot's
 *       Joran extensions to evaluate {@code <springProfile>} (raw Joran skips those tags).
 *   <li>The shared XML file itself is read as text and asserted to keep the MDC provider enabled
 *       (i.e. it must not contain {@code <includeMdc>false</includeMdc>}, which would disable
 *       MDC entirely and silently drop {@code requestId} / {@code userId} from logs).
 * </ol>
 */
class JsonLoggingConfigTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void logstashEncoder_emitsRequestIdUserIdServiceWithAllowlistedMdc() {
        LoggerContext ctx = new LoggerContext();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(ctx);
        encoder.setIncludeContext(false);
        encoder.setIncludeTags(false);
        encoder.addIncludeMdcKeyName("requestId");
        encoder.addIncludeMdcKeyName("userId");
        encoder.setCustomFields("{\"service\":\"listing-service\"}");
        // Mirror the <fieldNames> block from logback-shared.xml so the test asserts the same
        // wire format the services actually emit (no @version, no logger_name, no level_value).
        LogstashFieldNames names = new LogstashFieldNames();
        names.setTimestamp("timestamp");
        names.setMessage("message");
        names.setLogger("logger");
        names.setThread("thread");
        names.setLevelValue("[ignore]");
        names.setVersion("[ignore]");
        encoder.setFieldNames(names);
        encoder.start();

        Logger logger = ctx.getLogger("ru.hvostid.test");

        MDC.put("requestId", "req-abc");
        MDC.put("userId", "u-42");
        MDC.put("ignored", "should-not-appear");

        LoggingEvent event = new LoggingEvent("ru.hvostid.test", logger, Level.INFO, "Listing created", null, null);
        event.setLoggerContext(ctx);
        event.setMDCPropertyMap(MDC.getCopyOfContextMap());

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("\"requestId\":\"req-abc\"");
        assertThat(json).contains("\"userId\":\"u-42\"");
        assertThat(json).contains("\"service\":\"listing-service\"");
        assertThat(json).contains("\"message\":\"Listing created\"");
        assertThat(json).contains("\"level\":\"INFO\"");
        // Allowlist enforcement: an MDC key that is not requestId/userId must not leak.
        assertThat(json).doesNotContain("ignored");
        // Encoder noise that the shared config explicitly turns off.
        assertThat(json).doesNotContain("@version").doesNotContain("\"tags\"");
    }

    @Test
    void sharedXml_keepsMdcProviderEnabled() throws Exception {
        // includeMdc=false disables the MDC JSON provider entirely, which silently
        // drops includeMdcKeyName entries. Guard the actual file against this regression.
        Path shared = Path.of("src/main/resources/logback-shared.xml");
        String content = Files.readString(shared);
        assertThat(content).doesNotContain("<includeMdc>false</includeMdc>");
        assertThat(content).contains("<includeMdcKeyName>requestId</includeMdcKeyName>");
        assertThat(content).contains("<includeMdcKeyName>userId</includeMdcKeyName>");
    }
}
