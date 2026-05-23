package ru.hvostid.passport.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;

public interface PassportDocumentRepository extends JpaRepository<PassportDocument, Long> {
    interface StorageRef {
        Long getId();

        PassportDocumentType getType();

        String getStoragePath();
    }

    List<PassportDocument> findByPassportIdOrderByUploadedAtDesc(Long passportId);

    List<PassportDocument> findAllByPassportId(Long passportId);

    List<StorageRef> findAllProjectedByPassportId(Long passportId);

    Optional<PassportDocument> findByIdAndPassportId(Long id, Long passportId);

    Optional<PassportDocument> findFirstByPassportIdAndTypeOrderByUploadedAtAsc(
            Long passportId, PassportDocumentType type);
}
