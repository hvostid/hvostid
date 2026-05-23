package ru.hvostid.passport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.passport.client.ListingServiceClient;
import ru.hvostid.passport.config.MediaTicketProperties;
import ru.hvostid.passport.config.MinioProperties;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.repository.PassportDocumentRepository;
import ru.hvostid.passport.repository.PassportDocumentRepository.StorageRef;
import ru.hvostid.passport.service.PassportDocumentService.DocumentCleanupResult;
import ru.hvostid.passport.storage.MinioStorageException;
import ru.hvostid.passport.storage.MinioStorageService;
import ru.hvostid.passport.storage.PassportObjectNameFactory;

@ExtendWith(MockitoExtension.class)
class PassportDocumentServiceTest {
    private static final MinioProperties MINIO_PROPERTIES =
            new MinioProperties("http://minio:9000", "access", "secret", new MinioProperties.Buckets("docs", "photos"));

    @Mock
    private PassportAccessService accessService;

    @Mock
    private PassportDocumentRepository documentRepository;

    @Mock
    private PassportDocumentValidator validator;

    @Mock
    private PassportObjectNameFactory objectNameFactory;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private TrustScoreService trustScoreService;

    @Mock
    private ListingServiceClient listingServiceClient;

    @Mock
    private MediaTicketService mediaTicketService;

    @Mock
    private MediaTicketProperties mediaTicketProperties;

    private PassportDocumentService service;

    @BeforeEach
    void setUp() {
        service = new PassportDocumentService(
                accessService,
                documentRepository,
                validator,
                objectNameFactory,
                storageService,
                MINIO_PROPERTIES,
                trustScoreService,
                listingServiceClient,
                mediaTicketService,
                mediaTicketProperties);
    }

    @Test
    void deleteAllForPassportContinuesWhenStorageDeleteFails() {
        when(documentRepository.findAllProjectedByPassportId(1L))
                .thenReturn(List.of(
                        new TestStorageRef(10L, PassportDocumentType.PHOTO, "photo.jpg"),
                        new TestStorageRef(11L, PassportDocumentType.VET_RECORD, "record.pdf")));
        doAnswer(invocation -> {
                    String objectName = invocation.getArgument(1);
                    if ("record.pdf".equals(objectName)) {
                        throw new MinioStorageException("delete failed", new RuntimeException("boom"));
                    }
                    return null;
                })
                .when(storageService)
                .delete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        DocumentCleanupResult result = service.deleteAllForPassport(1L);

        assertThat(result.cleaned()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(storageService).delete("photos", "photo.jpg");
        verify(storageService).delete("docs", "record.pdf");
    }

    private record TestStorageRef(Long id, PassportDocumentType type, String storagePath) implements StorageRef {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public PassportDocumentType getType() {
            return type;
        }

        @Override
        public String getStoragePath() {
            return storagePath;
        }
    }
}
