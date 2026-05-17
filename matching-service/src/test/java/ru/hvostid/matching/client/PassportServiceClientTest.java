package ru.hvostid.matching.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static ru.hvostid.common.http.SecurityHeaders.REQUEST_ID;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PassportServiceClientTest {
    private MockRestServiceServer server;
    private PassportServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://passport");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PassportServiceClient(builder.build());
    }

    @Test
    @DisplayName("propagates X-Request-Id to internal endpoint")
    void getPassport_propagatesRequestId() {
        String body = """
                {"species":"dog","breed":"Husky","temperament":"active","specialNeeds":null}
                """;
        server.expect(requestTo("http://passport/internal/passports/3"))
                .andExpect(header(REQUEST_ID, "trace-xyz"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<PassportSnapshot> snapshot = client.getPassport(3L, "trace-xyz");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().breed()).isEqualTo("Husky");
        server.verify();
    }

    @Test
    @DisplayName("404 returns empty optional")
    void getPassport_notFound_returnsEmpty() {
        server.expect(requestTo("http://passport/internal/passports/404")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getPassport(404L, null)).isEmpty();
    }
}
