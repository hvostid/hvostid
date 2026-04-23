package ru.hvostid.listing.entity;

public enum ListingStatus {
    DRAFT,      // черновик
    MODERATION, // на модерации
    PUBLISHED,  // опубликовано
    REJECTED,   // отклонено модератором
    ARCHIVED    // архивировано
}