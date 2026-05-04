package ru.hvostid.passport.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PassportObjectNameFactoryTest {
    private final PassportObjectNameFactory factory = new PassportObjectNameFactory();

    @Test
    void createBuildsPassportScopedObjectNameWithLowercaseExtension() {
        UUID sellerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID passportId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        String objectName = factory.create(sellerId, passportId, "Vet Certificate.PDF");

        assertThat(objectName)
                .startsWith(sellerId + "/" + passportId + "/")
                .endsWith(".pdf")
                .matches(sellerId + "/" + passportId + "/[0-9a-f-]{36}\\.pdf");
    }

    @Test
    void createOmitsExtensionWhenFilenameHasNoExtension() {
        UUID sellerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID passportId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        String objectName = factory.create(sellerId, passportId, "document");

        assertThat(objectName)
                .startsWith(sellerId + "/" + passportId + "/")
                .matches(sellerId + "/" + passportId + "/[0-9a-f-]{36}");
    }
}
