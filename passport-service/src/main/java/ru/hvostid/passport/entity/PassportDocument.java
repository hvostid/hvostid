package ru.hvostid.passport.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "passport_documents")
public class PassportDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passport_id", nullable = false)
    private PetPassport passport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassportDocumentType type;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected PassportDocument() {}

    public PassportDocument(
            PetPassport passport,
            PassportDocumentType type,
            String originalFilename,
            String storagePath,
            String mimeType,
            long size) {
        this.passport = passport;
        this.type = type;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.size = size;
    }

    @PrePersist
    void prePersist() {
        uploadedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PetPassport getPassport() {
        return passport;
    }

    public PassportDocumentType getType() {
        return type;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PassportDocument that = (PassportDocument) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
