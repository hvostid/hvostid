package ru.hvostid.passport.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;

public interface PassportDocumentRepository extends JpaRepository<PassportDocument, Long> {
    long countByPassportId(Long passportId);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(d.size),0) from PassportDocument d where d.passport.id=:passportId")
    long totalBytes(Long passportId);

    interface CoverRef {
        Long getPassportId();

        Long getDocumentId();
    }

    @org.springframework.data.jpa.repository.Query(
            "select d.passport.id as passportId,min(d.id) as documentId from PassportDocument d where d.passport.id in :ids and d.type=ru.hvostid.passport.entity.PassportDocumentType.PHOTO group by d.passport.id")
    List<CoverRef> findCovers(java.util.Collection<Long> ids);

    interface StorageRef {
        Long getId();

        PassportDocumentType getType();

        String getStoragePath();
    }

    List<PassportDocument> findByPassportIdOrderByUploadedAtDesc(Long passportId);

    List<StorageRef> findAllProjectedByPassportId(Long passportId);

    Optional<PassportDocument> findByIdAndPassportId(Long id, Long passportId);

    Optional<PassportDocument> findFirstByPassportIdAndTypeOrderByUploadedAtAsc(
            Long passportId, PassportDocumentType type);
}
