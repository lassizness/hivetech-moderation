package ru.hivetech.moderation;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class ModerationAction {
    public long id;
    public String uuid;
    public String username;
    public String type;
    public String scope;
    public String server_code;
    public String reason;
    public String starts_at;
    public String expires_at;
    public String status;

    public boolean isBan() {
        return "ban".equalsIgnoreCase(type);
    }

    public boolean isMute() {
        return "mute".equalsIgnoreCase(type);
    }

    public boolean isActive(long nowMillis) {
        if (!"active".equalsIgnoreCase(status)) {
            return false;
        }

        Long starts = parseMillis(starts_at);
        if (starts != null && starts > nowMillis) {
            return false;
        }

        Long expires = parseMillis(expires_at);
        return expires == null || expires > nowMillis;
    }

    public String normalizedUuid() {
        return normalizeUuid(uuid);
    }

    public static String normalizeUuid(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("-", "").trim().toLowerCase();
    }

    public Long expiresAtMillis() {
        return parseMillis(expires_at);
    }

    private static Long parseMillis(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
