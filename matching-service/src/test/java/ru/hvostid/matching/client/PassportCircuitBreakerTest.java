package ru.hvostid.matching.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;

@SpringBootTest(
        properties = {
            "resilience4j.circuitbreaker.instances.passportService.minimum-number-of-calls=2",
            "resilience4j.circuitbreaker.instances.passportService.sliding-window-size=2",
            "resilience4j.circuitbreaker.instances.passportService.permitted-number-of-calls-in-half-open-state=2"
        })
class PassportCircuitBreakerTest extends AbstractPostgresContainerTest {
    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger(500);
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final HttpServer SERVER = createServer();

    @Autowired
    private PassportServiceClient client;

    @Autowired
    private CircuitBreakerRegistry registry;

    @DynamicPropertySource
    static void remoteUrl(DynamicPropertyRegistry properties) {
        properties.add(
                "hvostid.passport-service.url",
                () -> "http://localhost:" + SERVER.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        registry.circuitBreaker("passportService").reset();
        REQUESTS.set(0);
    }

    @AfterAll
    static void closeServer() {
        SERVER.stop(0);
    }

    @Test
    void serverErrorsOpenCircuitAndSuccessfulProbesCloseIt() {
        var circuit = registry.circuitBreaker("passportService");
        RESPONSE_STATUS.set(500);
        assertThat(client.getPassport(1, "failure-one")).isEmpty();
        assertThat(client.getPassport(1, "failure-two")).isEmpty();
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(client.getPassport(1, "open")).isEmpty();
        assertThat(REQUESTS.get()).isEqualTo(2);
        circuit.transitionToHalfOpenState();
        RESPONSE_STATUS.set(200);
        assertThat(client.getPassport(1, "probe-one")).isPresent();
        assertThat(client.getPassport(1, "probe-two")).isPresent();
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void notFoundDoesNotCountAsAvailabilityFailure() {
        RESPONSE_STATUS.set(404);
        for (int i = 0; i < 3; i++) {
            assertThat(client.getPassport(1, "missing")).isEmpty();
        }
        var circuit = registry.circuitBreaker("passportService");
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private static HttpServer createServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/passports/", exchange -> {
                REQUESTS.incrementAndGet();
                byte[] body = "{\"species\":\"cat\",\"breed\":\"Siamese\",\"temperament\":\"calm\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(RESPONSE_STATUS.get(), body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
