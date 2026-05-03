package ru.hvostid.auth.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.auth.repository.SessionRepository;

/**
 * Periodically removes expired sessions from the database.
 */
@Service
public class SessionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    private final SessionRepository sessionRepository;

    public SessionCleanupService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Runs every 15 minutes to delete sessions past their expiresAt.
     */
    @Scheduled(fixedRateString = "${hvostid.auth.cleanup-interval:PT15M}")
    @Transactional
    public void cleanupExpiredSessions() {
        int deleted = sessionRepository.deleteAllByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired session(s)", deleted);
        }
    }
}
