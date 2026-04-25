package ru.hvostid.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.auth.dto.AddRoleRequest;
import ru.hvostid.auth.dto.ProfileResponse;
import ru.hvostid.auth.dto.UpdateProfileRequest;
import ru.hvostid.auth.entity.User;
import ru.hvostid.auth.exception.ForbiddenRoleException;
import ru.hvostid.auth.exception.UserNotFoundException;
import ru.hvostid.auth.repository.UserRepository;
import ru.hvostid.common.security.UserRole;

import java.util.Set;

/**
 * Service handling user profile operations and role management.
 */
@Service
public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /**
     * Roles that can be self-assigned by a user.
     */
    private static final Set<UserRole> SELF_ASSIGNABLE_ROLES = Set.of(UserRole.SELLER);

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Get profile of the current user.
     *
     * @param userId authenticated user identifier
     * @return full profile data
     * @throws UserNotFoundException if user does not exist
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        log.debug("Fetching profile for userId={}", userId);
        User user = findUserOrThrow(userId);
        return toProfileResponse(user);
    }

    /**
     * Update profile fields for the current user.
     * Only non-null fields from the request are applied.
     *
     * @param userId  authenticated user identifier
     * @param request profile fields to update
     * @return updated profile data
     * @throws UserNotFoundException if user does not exist
     */
    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.debug("Updating profile for userId={}", userId);
        User user = findUserOrThrow(userId);

        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.city() != null) {
            user.setCity(request.city());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }

        user = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);
        return toProfileResponse(user);
    }

    /**
     * Add a role to the current user.
     * Only seller can be self-assigned; moderator and admin require admin privileges.
     *
     * @param userId  authenticated user identifier
     * @param request contains the role to add
     * @return updated profile data
     * @throws UserNotFoundException  if user does not exist
     * @throws ForbiddenRoleException if the role cannot be self-assigned
     */
    @Transactional
    public ProfileResponse addRole(Long userId, AddRoleRequest request) {
        log.debug("Adding role={} for userId={}", request.role(), userId);
        User user = findUserOrThrow(userId);

        UserRole role;
        try {
            role = UserRole.fromValue(request.role());
        } catch (IllegalArgumentException _) {
            throw new ForbiddenRoleException(request.role());
        }

        if (!SELF_ASSIGNABLE_ROLES.contains(role)) {
            log.warn("Attempt to self-assign restricted role={} by userId={}", role, userId);
            throw new ForbiddenRoleException(request.role());
        }

        user.addRole(role);
        user = userRepository.save(user);
        log.info("Role={} added for userId={}", role, userId);
        return toProfileResponse(user);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found userId={}", userId);
                    return new UserNotFoundException(userId);
                });
    }

    private ProfileResponse toProfileResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoles().stream()
                        .map(UserRole::lowerValue)
                        .sorted()
                        .toList(),
                user.getPhone(),
                user.getCity(),
                user.getBio(),
                user.getRating()
        );
    }
}
