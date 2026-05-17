package ru.hvostid.passport.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PassportObjectNameFactoryTest {
    private final PassportObjectNameFactory factory = new PassportObjectNameFactory();

    @Test
    void createBuildsPassportScopedObjectNameWithLowercaseExtension() {
        long sellerId = 1L;
        long passportId = 2L;

        String objectName = factory.create(sellerId, passportId, "Vet Certificate.PDF");

        assertThat(objectName)
                .startsWith(sellerId + "/" + passportId + "/")
                .endsWith(".pdf")
                .matches(sellerId + "/" + passportId + "/[0-9a-f-]{36}\\.pdf");
    }

    @Test
    void createOmitsExtensionWhenFilenameHasNoExtension() {
        long sellerId = 1L;
        long passportId = 2L;

        String objectName = factory.create(sellerId, passportId, "document");

        assertThat(objectName)
                .startsWith(sellerId + "/" + passportId + "/")
                .matches(sellerId + "/" + passportId + "/[0-9a-f-]{36}");
    }
}
