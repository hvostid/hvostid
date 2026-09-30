package ru.hvostid.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_tokens")
public class AccountToken {
    public enum Purpose {
        PASSWORD_RESET,
        EMAIL_VERIFICATION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountToken() {}

    public AccountToken(User user, String tokenHash, Purpose purpose, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Long getId() {
        return id;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
