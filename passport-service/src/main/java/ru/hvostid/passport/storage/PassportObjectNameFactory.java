package ru.hvostid.passport.storage;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PassportObjectNameFactory {
    public String create(Long sellerId, Long passportId, String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String suffix = extension == null || extension.isBlank() ? "" : "." + extension.toLowerCase(Locale.ROOT);
        return sellerId + "/" + passportId + "/" + UUID.randomUUID() + suffix;
    }
}
