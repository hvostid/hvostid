package ru.hvostid.auth.dto;

import java.time.Instant;

public record SessionResponse(Long id, Instant createdAt, Instant expiresAt) {}
