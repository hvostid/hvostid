package ru.hvostid.passport.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.hvostid.passport.client.ListingServiceClient;
import ru.hvostid.passport.config.MinioProperties;
import ru.hvostid.passport.dto.PassportDocumentResponse;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.InvalidPassportDocumentException;
import ru.hvostid.passport.exception.PassportDocumentNotFoundException;
import ru.hvostid.passport.exception.PassportNotFoundException;
import ru.hvostid.passport.repository.PassportDocumentRepository;
import ru.hvostid.passport.storage.MinioStorageService;
import ru.hvostid.passport.storage.PassportObjectNameFactory;

@Service
public class PassportDocumentService {
    private static final Logger log = LoggerFactory.getLogger(PassportDocumentService.class);
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(10);

    private final PassportAccessService accessService;
    private final PassportDocumentRepository documentRepository;
    private final PassportDocumentValidator validator;
    private final PassportObjectNameFactory objectNameFactory;
    private final MinioStorageService storageService;
    private final MinioProperties minioProperties;
    private final TrustScoreService trustScoreService;
    private final ListingServiceClient listingServiceClient;

    public PassportDocumentService(
            PassportAccessService accessService,
            PassportDocumentRepository documentRepository,
            PassportDocumentValidator validator,
            PassportObjectNameFactory objectNameFactory,
            MinioStorageService storageService,
            MinioProperties minioProperties,
            TrustScoreService trustScoreService,
            ListingServiceClient listingServiceClient) {
        this.accessService = accessService;
        this.documentRepository = documentRepository;
        this.validator = validator;
        this.objectNameFactory = objectNameFactory;
        this.storageService = storageService;
        this.minioProperties = minioProperties;
        this.trustScoreService = trustScoreService;
        this.listingServiceClient = listingServiceClient;
    }

    @Transactional
    public PassportDocumentResponse uploadDocument(
            Long passportId, MultipartFile file, PassportDocumentType type, Long userId) {
        Objects.requireNonNull(type, "type must not be null");
        validator.validate(file);
        PetPassport passport = accessService.getExistingPassport(passportId);
        accessService.requireOwner(passport, userId, "upload documents to");

        String bucket = bucketFor(type);
        String storagePath =
                objectNameFactory.create(passport.getSellerId(), passport.getId(), file.getOriginalFilename());
        log.debug("Uploading passport document passportId={} type={} bucket={}", passportId, type, bucket);

        try (var inputStream = file.getInputStream()) {
            storageService.upload(bucket, storagePath, inputStream, file.getSize(), file.getContentType());
        } catch (IOException ex) {
            throw new InvalidPassportDocumentException("Failed to read document file", ex);
        }

        try {
            PassportDocument document = new PassportDocument(
                    passport, type, file.getOriginalFilename(), storagePath, file.getContentType(), file.getSize());
            PassportDocument saved = documentRepository.saveAndFlush(document);
            log.info("Passport document uploaded id={} passportId={} type={}", saved.getId(), passportId, type);
            trustScoreService.recalculate(passportId);
            return PassportDocumentResponse.from(saved);
        } catch (RuntimeException ex) {
            deleteUploadedObjectAfterMetadataFailure(bucket, storagePath, ex);
            throw ex;
        }
    }

    /**
     * Lists documents attached to a passport.
     *
     * <p>Privileged callers (owner / MODERATOR / ADMIN) see every document.
     * Other authenticated callers see only PHOTO entries, and only when the
     * passport is referenced by at least one PUBLISHED listing -- the same
     * existence-hiding rule trust-score uses. Mismatches throw 404 rather
     * than 403 so anonymous probers cannot enumerate passport ids.
     *
     * <p>Every entry returned carries a short-TTL presigned MinIO URL so the
     * caller can use it directly (e.g. as an {@code <img src>}) without an
     * extra round-trip through this service.
     *
     * <p>Intentionally not annotated with {@code @Transactional}: the
     * buyer-path access check makes a synchronous HTTP call to
     * listing-service, which would otherwise hold a Hikari connection for
     * the full round-trip. Each repository call below opens its own short
     * transaction via Spring Data JPA defaults.
     */
    public List<PassportDocumentResponse> listDocuments(
            Long passportId, Long userId, Set<String> userRoles, String requestId) {
        PetPassport passport = accessService.getExistingPassport(passportId);
        boolean privileged = accessService.isPrivilegedViewer(passport, userId, userRoles);
        if (!privileged && !listingServiceClient.hasPublishedListingForPassport(passportId, requestId)) {
            log.warn(
                    "Document list denied passportId={} userId={} (no PUBLISHED listing reference)",
                    passportId,
                    userId);
            throw new PassportNotFoundException("Passport not found with id: " + passportId);
        }

        return documentRepository.findByPassportIdOrderByUploadedAtDesc(passportId).stream()
                .filter(doc -> privileged || doc.getType() == PassportDocumentType.PHOTO)
                .map(doc -> PassportDocumentResponse.from(doc, presignedUrlFor(doc)))
                .toList();
    }

    /**
     * Returns a presigned MinIO URL for a single document.
     *
     * <p>Owner / MODERATOR / ADMIN can download any document type. Other
     * authenticated callers can download a PHOTO document attached to a
     * passport that is referenced by at least one PUBLISHED listing; every
     * other combination throws 404 (hide existence; see
     * {@link #listDocuments}).
     *
     * <p>Not annotated with {@code @Transactional} for the same reason as
     * {@link #listDocuments}.
     */
    public String getDownloadUrl(
            Long passportId, Long documentId, Long userId, Set<String> userRoles, String requestId) {
        PetPassport passport = accessService.getExistingPassport(passportId);
        PassportDocument document = getDocument(passportId, documentId);
        if (!accessService.isPrivilegedViewer(passport, userId, userRoles)) {
            if (document.getType() != PassportDocumentType.PHOTO
                    || !listingServiceClient.hasPublishedListingForPassport(passportId, requestId)) {
                log.warn(
                        "Document download denied passportId={} documentId={} type={} userId={}",
                        passportId,
                        documentId,
                        document.getType(),
                        userId);
                throw new PassportDocumentNotFoundException("Passport document not found with id: " + documentId);
            }
        }
        return presignedUrlFor(document);
    }

    private String presignedUrlFor(PassportDocument document) {
        return storageService.getPresignedUrl(
                bucketFor(document.getType()), document.getStoragePath(), DOWNLOAD_URL_EXPIRY);
    }

    @Transactional
    public void deleteDocument(Long passportId, Long documentId, Long userId) {
        PetPassport passport = accessService.getExistingPassport(passportId);
        accessService.requireOwner(passport, userId, "delete documents from");
        PassportDocument document = getDocument(passportId, documentId);
        documentRepository.delete(document);
        storageService.delete(bucketFor(document.getType()), document.getStoragePath());
        log.info("Passport document deleted id={} passportId={}", documentId, passportId);
        trustScoreService.recalculate(passportId);
    }

    private PassportDocument getDocument(Long passportId, Long documentId) {
        return documentRepository
                .findByIdAndPassportId(documentId, passportId)
                .orElseThrow(() ->
                        new PassportDocumentNotFoundException("Passport document not found with id: " + documentId));
    }

    private String bucketFor(PassportDocumentType type) {
        return switch (type) {
            case PHOTO -> minioProperties.buckets().photos();
            case VACCINATION_CERT, VET_RECORD, OTHER ->
                minioProperties.buckets().documents();
        };
    }

    private void deleteUploadedObjectAfterMetadataFailure(String bucket, String storagePath, RuntimeException cause) {
        try {
            storageService.delete(bucket, storagePath);
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to clean up uploaded passport document after metadata failure", cleanupEx);
            cause.addSuppressed(cleanupEx);
        }
    }
}
