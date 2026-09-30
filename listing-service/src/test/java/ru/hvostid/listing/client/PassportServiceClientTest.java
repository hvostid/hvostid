package ru.hvostid.listing.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.hvostid.common.exception.ConflictException;
import ru.hvostid.common.exception.ValidationException;

class PassportServiceClientTest {
    private MockRestServiceServer server;
    private PassportServiceClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("http://passport");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PassportServiceClient(builder.build());
    }

    @Test
    void ownerProjectionAcceptsFullPassportButRejectsAnotherOwner() {
        server.expect(requestTo("http://passport/internal/passports/42"))
                .andRespond(withSuccess("{\"id\":42,\"sellerId\":7,\"species\":\"dog\"}", MediaType.APPLICATION_JSON));
        assertThatCode(() -> client.validateOwner(42, 7)).doesNotThrowAnyException();
        server.verify();
        server.reset();
        server.expect(requestTo("http://passport/internal/passports/42"))
                .andRespond(withSuccess("{\"sellerId\":8}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.validateOwner(42, 7)).isInstanceOf(ValidationException.class);
        server.verify();
    }

    @Test
    void acquisitionValidatesOnPassportServiceAndPreservesAvailabilityErrors() {
        server.expect(requestTo("http://passport/internal/passports/42/references/12"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"sellerId\":7,\"approved\":true,\"revision\":2}"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> client.acquire(42, 12, 7, true, 2)).isInstanceOf(ConflictException.class);
        server.verify();
        server.reset();
        server.expect(requestTo("http://passport/internal/passports/42")).andRespond(withServerError());
        assertThatThrownBy(() -> client.validateOwner(42, 7))
                .isInstanceOf(PassportServiceClient.PassportServiceUnavailableException.class);
        server.verify();
    }
}
