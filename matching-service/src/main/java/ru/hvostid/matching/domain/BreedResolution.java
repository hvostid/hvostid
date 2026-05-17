package ru.hvostid.matching.domain;

/**
 * Result of resolving a pet's species/breed to a {@link BreedProfile}.
 *
 * @param profile the resolved profile (never null; falls back to NEUTRAL_PROFILE)
 * @param speciesUnknown true when the species could not be matched to a known cat/dog profile
 */
public record BreedResolution(BreedProfile profile, boolean speciesUnknown) {}
