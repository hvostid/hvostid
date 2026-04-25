package ru.hvostid.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.auth.dto.AddRoleRequest;
import ru.hvostid.auth.dto.ProfileResponse;
import ru.hvostid.auth.dto.UpdateProfileRequest;
import ru.hvostid.auth.entity.User;
import ru.hvostid.auth.exception.ForbiddenRoleException;
import ru.hvostid.auth.exception.UserNotFoundException;
import ru.hvostid.auth.repository.UserRepository;
import ru.hvostid.common.security.UserRole;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock
    private UserRepository userRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userRepository);
    }

    // -- Helper to create a user with id and default BUYER role --

    private User createUser(Long id, String email, String name) {
        User user = new User(email, name, "hashed_password");
        user.setId(id);
        return user;
    }

    // -- getProfile tests --

    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {
        @Test
        @DisplayName("existing user - returns profile with all fields")
        void getProfile_existingUser_returnsProfile() {
            User user = createUser(1L, "test@example.com", "Test User");
            user.setPhone("+79001234567");
            user.setCity("Moscow");
            user.setBio("Hello world");
            user.setRating(4.5);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ProfileResponse response = profileService.getProfile(1L);

            assertEquals(1L, response.id());
            assertEquals("test@example.com", response.email());
            assertEquals("Test User", response.name());
            assertTrue(response.roles().contains("buyer"));
            assertEquals("+79001234567", response.phone());
            assertEquals("Moscow", response.city());
            assertEquals("Hello world", response.bio());
            assertEquals(4.5, response.rating());
        }

        @Test
        @DisplayName("existing user with null optional fields - returns profile with nulls")
        void getProfile_nullOptionalFields_returnsProfileWithNulls() {
            User user = createUser(2L, "minimal@example.com", "Minimal");
            when(userRepository.findById(2L)).thenReturn(Optional.of(user));

            ProfileResponse response = profileService.getProfile(2L);

            assertEquals(2L, response.id());
            assertEquals("minimal@example.com", response.email());
            assertNull(response.phone());
            assertNull(response.city());
            assertNull(response.bio());
            assertNull(response.rating());
        }

        @Test
        @DisplayName("non-existent user - throws UserNotFoundException")
        void getProfile_nonExistentUser_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> profileService.getProfile(999L));
        }

        @Test
        @DisplayName("default role is buyer")
        void getProfile_defaultRole_isBuyer() {
            User user = createUser(1L, "buyer@example.com", "Buyer");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ProfileResponse response = profileService.getProfile(1L);

            assertEquals(1, response.roles().size());
            assertTrue(response.roles().contains("buyer"));
        }

        @Test
        @DisplayName("user with multiple roles - returns sorted roles")
        void getProfile_multipleRoles_returnsSorted() {
            User user = createUser(1L, "multi@example.com", "Multi");
            user.addRole(UserRole.SELLER);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ProfileResponse response = profileService.getProfile(1L);

            assertEquals(2, response.roles().size());
            // roles should be sorted alphabetically
            assertEquals("buyer", response.roles().get(0));
            assertEquals("seller", response.roles().get(1));
        }
    }

    // -- updateProfile tests --

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {
        @Test
        @DisplayName("update all fields - applies all changes")
        void updateProfile_allFields_appliesAll() {
            User user = createUser(1L, "test@example.com", "Old Name");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "New Name", "+79001234567", "Moscow", "My bio"
            );

            ProfileResponse response = profileService.updateProfile(1L, request);

            assertEquals("New Name", response.name());
            assertEquals("+79001234567", response.phone());
            assertEquals("Moscow", response.city());
            assertEquals("My bio", response.bio());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("update only name - other fields unchanged")
        void updateProfile_onlyName_othersUnchanged() {
            User user = createUser(1L, "test@example.com", "Old Name");
            user.setPhone("+79001234567");
            user.setCity("Moscow");
            user.setBio("Old bio");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "New Name", null, null, null
            );

            ProfileResponse response = profileService.updateProfile(1L, request);

            assertEquals("New Name", response.name());
            assertEquals("+79001234567", response.phone());
            assertEquals("Moscow", response.city());
            assertEquals("Old bio", response.bio());
        }

        @Test
        @DisplayName("update with all nulls - nothing changes")
        void updateProfile_allNulls_nothingChanges() {
            User user = createUser(1L, "test@example.com", "Unchanged");
            user.setPhone("+79001234567");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest(null, null, null, null);

            ProfileResponse response = profileService.updateProfile(1L, request);

            assertEquals("Unchanged", response.name());
            assertEquals("+79001234567", response.phone());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("non-existent user - throws UserNotFoundException")
        void updateProfile_nonExistentUser_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "Name", null, null, null
            );

            assertThrows(UserNotFoundException.class,
                    () -> profileService.updateProfile(999L, request));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("update does not modify email or roles")
        void updateProfile_doesNotModifyEmailOrRoles() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "New Name", "+70001112233", "SPb", "Bio"
            );

            ProfileResponse response = profileService.updateProfile(1L, request);

            assertEquals("test@example.com", response.email());
            assertTrue(response.roles().contains("buyer"));
        }
    }

    // -- addRole tests --

    @Nested
    @DisplayName("addRole")
    class AddRoleTests {
        @Test
        @DisplayName("add seller role - success")
        void addRole_seller_success() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            ProfileResponse response = profileService.addRole(1L, new AddRoleRequest("seller"));

            assertTrue(response.roles().contains("seller"));
            assertTrue(response.roles().contains("buyer"));
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("add seller role case-insensitive - success")
        void addRole_sellerUpperCase_success() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            ProfileResponse response = profileService.addRole(1L, new AddRoleRequest("SELLER"));

            assertTrue(response.roles().contains("seller"));
        }

        @Test
        @DisplayName("add seller twice - idempotent, no error")
        void addRole_sellerTwice_idempotent() {
            User user = createUser(1L, "test@example.com", "Test");
            user.addRole(UserRole.SELLER);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            ProfileResponse response = profileService.addRole(1L, new AddRoleRequest("seller"));

            // Set-based roles: adding existing role is a no-op
            long sellerCount = response.roles().stream()
                    .filter("seller"::equals)
                    .count();
            assertEquals(1, sellerCount);
        }

        @Test
        @DisplayName("add moderator role - throws ForbiddenRoleException")
        void addRole_moderator_throws() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            AddRoleRequest request = new AddRoleRequest("moderator");
            assertThrows(ForbiddenRoleException.class,
                    () -> profileService.addRole(1L, request));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("add admin role - throws ForbiddenRoleException")
        void addRole_admin_throws() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            AddRoleRequest request = new AddRoleRequest("admin");
            assertThrows(ForbiddenRoleException.class,
                    () -> profileService.addRole(1L, request));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("add buyer role - throws ForbiddenRoleException (already default)")
        void addRole_buyer_throws() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // BUYER is not in SELF_ASSIGNABLE_ROLES, so it throws
            AddRoleRequest request = new AddRoleRequest("buyer");
            assertThrows(ForbiddenRoleException.class,
                    () -> profileService.addRole(1L, request));
        }

        @Test
        @DisplayName("add unknown role - throws ForbiddenRoleException")
        void addRole_unknownRole_throws() {
            User user = createUser(1L, "test@example.com", "Test");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            AddRoleRequest request = new AddRoleRequest("superadmin");
            assertThrows(ForbiddenRoleException.class,
                    () -> profileService.addRole(1L, request));
        }

        @Test
        @DisplayName("non-existent user - throws UserNotFoundException")
        void addRole_nonExistentUser_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            AddRoleRequest request = new AddRoleRequest("seller");
            assertThrows(UserNotFoundException.class,
                    () -> profileService.addRole(999L, request));
        }
    }
}
