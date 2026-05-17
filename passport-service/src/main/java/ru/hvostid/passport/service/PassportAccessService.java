package ru.hvostid.passport.service;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.PassportAccessDeniedException;
import ru.hvostid.passport.exception.PassportNotFoundException;
import ru.hvostid.passport.repository.PetPassportRepository;

@Service
public class PassportAccessService {
    private static final Logger log = LoggerFactory.getLogger(PassportAccessService.class);

    private final PetPassportRepository passportRepository;

    public PassportAccessService(PetPassportRepository passportRepository) {
        this.passportRepository = passportRepository;
    }

    @Transactional(readOnly = true)
    public PetPassport getExistingPassport(Long passportId) {
        return passportRepository
                .findById(passportId)
                .orElseThrow(() -> new PassportNotFoundException("Passport not found with id: " + passportId));
    }

    public void requireOwner(PetPassport passport, Long userId, String action) {
        if (!passport.getSellerId().equals(userId)) {
            log.warn(
                    "{} denied passportId={} ownerId={} userId={}",
                    action,
                    passport.getId(),
                    passport.getSellerId(),
                    userId);
            throw new PassportAccessDeniedException("You don't have permission to " + action + " this passport");
        }
    }

    public void requireCanView(PetPassport passport, Long userId, Set<String> userRoles) {
        if (!canViewPassport(passport, userId, userRoles)) {
            log.warn(
                    "Passport view denied id={} ownerId={} userId={}",
                    passport.getId(),
                    passport.getSellerId(),
                    userId);
            throw new PassportAccessDeniedException("You don't have permission to view this passport");
        }
    }

    private boolean canViewPassport(PetPassport passport, Long userId, Set<String> userRoles) {
        return passport.getSellerId().equals(userId)
                || userRoles.contains(UserRole.ADMIN.value())
                || userRoles.contains(UserRole.MODERATOR.value());
    }
}
