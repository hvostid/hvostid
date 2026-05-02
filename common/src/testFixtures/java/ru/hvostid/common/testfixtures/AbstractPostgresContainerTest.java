package ru.hvostid.common.testfixtures;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractPostgresContainerTest {
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES;
    private static final String IMAGE_PROPERTY = "testcontainers.postgres.image";

    static {
        String image = System.getProperty(IMAGE_PROPERTY);
        if (image == null || image.isBlank()) {
            throw new IllegalStateException(
                    "System property '" + IMAGE_PROPERTY + "' is not set. " +
                            "Run tests via Gradle so the image is injected from libs.versions.toml."
            );
        }
        POSTGRES = new PostgreSQLContainer(DockerImageName.parse(image));
        POSTGRES.start();
    }
}
