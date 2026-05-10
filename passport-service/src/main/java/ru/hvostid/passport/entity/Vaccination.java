package ru.hvostid.passport.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "vaccinations")
public class Vaccination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passport_id", nullable = false)
    private PetPassport passport;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "next_date")
    private LocalDate nextDate;

    @Column(nullable = false)
    private boolean verified;

    protected Vaccination() {}

    public Vaccination(PetPassport passport, String name, LocalDate date, LocalDate nextDate, boolean verified) {
        this.passport = passport;
        this.name = name;
        this.date = date;
        this.nextDate = nextDate;
        this.verified = verified;
    }

    public Long getId() {
        return id;
    }

    public PetPassport getPassport() {
        return passport;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalDate getNextDate() {
        return nextDate;
    }

    public boolean isVerified() {
        return verified;
    }
}
