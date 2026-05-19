package ru.hvostid.passport.service;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.hvostid.common.http.ProxyHeaders;
import ru.hvostid.passport.config.MediaTicketProperties;
import ru.hvostid.passport.config.MinioProperties;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.storage.MinioStorageService;

/**
 * Builds the {@code 204 No Content + X-Accel-Redirect} response shape that
 * passport-service uses to hand byte streaming off to the frontend nginx for
 * every media endpoint (per-document ticket flow and public cover photos).
 *
 * <p>Generating a presigned URL and rewriting it into the
 * {@code internal-redirect-prefix} location is identical across callers; this
 * component is the single source of truth so the two controllers stay aligned
 * if the prefix or header set ever changes.
 */
@Component
public class MediaStreamRedirectFactory {
    private final MinioStorageService storageService;
    private final MinioProperties minioProperties;
    private final MediaTicketProperties properties;

    public MediaStreamRedirectFactory(
            MinioStorageService storageService, MinioProperties minioProperties, MediaTicketProperties properties) {
        this.storageService = storageService;
        this.minioProperties = minioProperties;
        this.properties = properties;
    }

    public ResponseEntity<Void> noContentXAccelRedirect(PassportDocument document) {
        String bucket = minioProperties.buckets().forDocumentType(document.getType());
        String presigned = storageService.getPresignedUrl(bucket, document.getStoragePath(), properties.presignTtl());
        String internalPath = toInternalRedirect(presigned);
        return ResponseEntity.noContent()
                .header(ProxyHeaders.X_ACCEL_REDIRECT, internalPath)
                .header(ProxyHeaders.REFERRER_POLICY, "no-referrer")
                .build();
    }

    /**
     * Converts the MinIO presigned URL into the path that the frontend nginx
     * proxies through its internal-only location. The query string carries
     * the AWS SigV4 signature unchanged so MinIO accepts the subrequest.
     */
    private String toInternalRedirect(String presignedUrl) {
        URI uri = URI.create(presignedUrl);
        String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        return properties.internalRedirectPrefix() + pathAndQuery;
    }
}
