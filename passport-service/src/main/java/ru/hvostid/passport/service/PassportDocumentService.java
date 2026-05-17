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
import ru.hvostid.passport.config.MinioProperties;
import ru.hvostid.passport.dto.PassportDocumentResponse;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.InvalidPassportDocumentException;
import ru.hvostid.passport.exception.PassportDocumentNotFoundException;
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

    public PassportDocumentService(
            PassportAccessService accessService,
            PassportDocumentRepository documentRepository,
            PassportDocumentValidator validator,
            PassportObjectNameFactory objectNameFactory,
            MinioStorageService storageService,
            MinioProperties minioProperties) {
        this.accessService = accessService;
        this.documentRepository = documentRepository;
        this.validator = validator;
        this.objectNameFactory = objectNameFactory;
        this.storageService = storageService;
        this.minioProperties = minioProperties;
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
            return PassportDocumentResponse.from(saved);
        } catch (RuntimeException ex) {
            deleteUploadedObjectAfterMetadataFailure(bucket, storagePath, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<PassportDocumentResponse> listDocuments(Long passportId, Long userId, Set<String> userRoles) {
        PetPassport passport = accessService.getExistingPassport(passportId);
        accessService.requireCanView(passport, userId, userRoles);
        return documentRepository.findByPassportIdOrderByUploadedAtDesc(passportId).stream()
                .map(PassportDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(Long passportId, Long documentId, Long userId, Set<String> userRoles) {
        PetPassport passport = accessService.getExistingPassport(passportId);
        accessService.requireCanView(passport, userId, userRoles);
        PassportDocument document = getDocument(passportId, documentId);
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
