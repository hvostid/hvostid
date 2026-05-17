package ru.hvostid.matching.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static ru.hvostid.common.http.SecurityHeaders.REQUEST_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.hvostid.matching.exception.ListingNotFoundException;

class ListingServiceClientTest {
    private MockRestServiceServer server;
    private ListingServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://listing");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ListingServiceClient(builder.build());
    }

    @Test
    @DisplayName("propagates X-Request-Id and X-User-Id")
    void getListing_propagatesHeaders() {
        String body = """
                {"id":1,"species":"dog","breed":"Husky","age":12,"passportId":"5"}
                """;
        server.expect(requestTo("http://listing/api/v1/listings/1"))
                .andExpect(header(REQUEST_ID, "trace-abc"))
                .andExpect(header(USER_ID, "42"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ListingSnapshot snapshot = client.getListing(1L, 42L, "trace-abc");

        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.breed()).isEqualTo("Husky");
        server.verify();
    }

    @Test
    @DisplayName("404 maps to ListingNotFoundException")
    void getListing_notFound() {
        server.expect(requestTo("http://listing/api/v1/listings/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getListing(99L, 1L, null)).isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    @DisplayName("403 maps to ListingNotFoundException")
    void getListing_forbidden_mapsToNotFound() {
        server.expect(requestTo("http://listing/api/v1/listings/5")).andRespond(withStatus(FORBIDDEN));

        assertThatThrownBy(() -> client.getListing(5L, 1L, "trace-1")).isInstanceOf(ListingNotFoundException.class);
    }
}
