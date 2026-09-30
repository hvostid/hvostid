package ru.hvostid.passport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static ru.hvostid.common.http.SecurityHeaders.REQUEST_ID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.hvostid.passport.exception.ListingServiceUnavailableException;

class ListingServiceClientTest {
    private MockRestServiceServer server;
    private ListingServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://listing");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ListingServiceClient(builder.build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void checksInternalUsageAndPropagatesRequestId(boolean active) {
        server.expect(requestTo("http://listing/internal/listings/passports/42/has-active"))
                .andExpect(header(REQUEST_ID, "trace-42"))
                .andRespond(withSuccess("{\"hasActiveListing\":" + active + "}", MediaType.APPLICATION_JSON));

        assertThat(client.hasActiveListingForPassport(42L, "trace-42")).isEqualTo(active);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"hasActiveListing\":null}", "not-json"})
    void invalidResponseDoesNotAuthorizeDeletion(String body) {
        server.expect(requestTo("http://listing/internal/listings/passports/42/has-active"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.hasActiveListingForPassport(42L, null))
                .isInstanceOf(ListingServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void publicEvidenceCheckIsBoundToTheActualPassportOwner() {
        server.expect(requestTo("http://listing/api/v1/listings/passports/42/has-published?sellerId=10"))
                .andExpect(header(REQUEST_ID, "trace-42"))
                .andRespond(withSuccess(
                        "{\"passportId\":\"42\",\"hasPublishedListing\":false}", MediaType.APPLICATION_JSON));
        assertThat(client.hasPublishedListingForPassport(42L, 10L, "trace-42")).isFalse();
        server.verify();
    }

    @Test
    void upstreamFailureDoesNotAuthorizeDeletion() {
        server.expect(requestTo("http://listing/internal/listings/passports/42/has-active"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.hasActiveListingForPassport(42L, null))
                .isInstanceOf(ListingServiceUnavailableException.class);
        server.verify();
    }
}
