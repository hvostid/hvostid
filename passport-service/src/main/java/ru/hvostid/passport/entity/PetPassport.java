package ru.hvostid.passport.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pet_passports")
public class PetPassport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String species;

    private String breed;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    private String color;

    @Column(length = 1000)
    private String temperament;

    @Column(name = "special_needs", length = 1000)
    private String specialNeeds;

    @Column(nullable = false)
    private boolean neutered;

    @Column(nullable = false)
    private boolean microchipped;

    @Column(name = "trust_score", nullable = false)
    private int trustScore;

    @Column(nullable = false)
    private boolean moderated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "passport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("date DESC")
    private List<Vaccination> vaccinations = new ArrayList<>();

    protected PetPassport() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long sellerId;
        private String species;
        private String breed;
        private String name;
        private LocalDate birthDate;
        private Gender gender;
        private String color;
        private String temperament;
        private String specialNeeds;
        private boolean neutered;
        private boolean microchipped;

        private Builder() {}

        public Builder sellerId(Long sellerId) {
            this.sellerId = sellerId;
            return this;
        }

        public Builder species(String species) {
            this.species = species;
            return this;
        }

        public Builder breed(String breed) {
            this.breed = breed;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder birthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder gender(Gender gender) {
            this.gender = gender;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder temperament(String temperament) {
            this.temperament = temperament;
            return this;
        }

        public Builder specialNeeds(String specialNeeds) {
            this.specialNeeds = specialNeeds;
            return this;
        }

        public Builder neutered(boolean neutered) {
            this.neutered = neutered;
            return this;
        }

        public Builder microchipped(boolean microchipped) {
            this.microchipped = microchipped;
            return this;
        }

        public PetPassport build() {
            PetPassport passport = new PetPassport();
            passport.sellerId = this.sellerId;
            passport.species = this.species;
            passport.breed = this.breed;
            passport.name = this.name;
            passport.birthDate = this.birthDate;
            passport.gender = this.gender;
            passport.color = this.color;
            passport.temperament = this.temperament;
            passport.specialNeeds = this.specialNeeds;
            passport.neutered = this.neutered;
            passport.microchipped = this.microchipped;
            return passport;
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getTemperament() {
        return temperament;
    }

    public void setTemperament(String temperament) {
        this.temperament = temperament;
    }

    public String getSpecialNeeds() {
        return specialNeeds;
    }

    public void setSpecialNeeds(String specialNeeds) {
        this.specialNeeds = specialNeeds;
    }

    public boolean isNeutered() {
        return neutered;
    }

    public void setNeutered(boolean neutered) {
        this.neutered = neutered;
    }

    public boolean isMicrochipped() {
        return microchipped;
    }

    public void setMicrochipped(boolean microchipped) {
        this.microchipped = microchipped;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(int trustScore) {
        this.trustScore = trustScore;
    }

    public boolean isModerated() {
        return moderated;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Vaccination> getVaccinations() {
        return vaccinations;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PetPassport that = (PetPassport) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
