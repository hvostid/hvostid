package ru.hvostid.listing;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.client.PassportServiceClient;

public abstract class ListingIntegrationTest extends AbstractPostgresContainerTest {
    @MockitoBean
    protected PassportServiceClient passportServiceClient;
}
