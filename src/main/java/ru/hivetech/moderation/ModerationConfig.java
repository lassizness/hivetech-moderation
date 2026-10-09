package ru.hivetech.moderation;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class ModerationConfig {
    private final File file;

    private boolean enabled;
    private String apiUrl;
    private String serverCode;
    private String token;
    private int syncIntervalSeconds;
    private int heartbeatIntervalSeconds;
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;

    public ModerationConfig(File file) {
        this.file = file;
    }

    public void load() {
        Configuration cfg = new Configuration(file);
        cfg.load();

        enabled = cfg.getBoolean(
            "enabled",
            "bridge",
            true,
            "Enable HiveTech Moderation Bridge"
        );

        apiUrl = trimSlash(cfg.getString(
            "apiUrl",
            "api",
            "https://hivegrid.online/internal/game",
            "HiveTech internal game API base URL"
        ));

        serverCode = cfg.getString(
            "serverCode",
            "api",
            "main",
            "Server code from HiveTech admin"
        ).trim();

        token = cfg.getString(
            "token",
            "api",
            "",
            "Per-server Bridge token. Never commit this value."
        ).trim();

        syncIntervalSeconds = cfg.getInt(
            "syncIntervalSeconds",
            "timing",
            3,
            1,
            60,
            "Moderation changes polling interval"
        );

        heartbeatIntervalSeconds = cfg.getInt(
            "heartbeatIntervalSeconds",
            "timing",
            60,
            15,
            600,
            "Heartbeat interval"
        );

        connectTimeoutSeconds = cfg.getInt(
            "connectTimeoutSeconds",
            "http",
            3,
            1,
            30,
            "HTTP connect timeout"
        );

        readTimeoutSeconds = cfg.getInt(
            "readTimeoutSeconds",
            "http",
            5,
            1,
            60,
            "HTTP read timeout"
        );

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    private static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public boolean isEnabled() { return enabled; }
    public String getApiUrl() { return apiUrl; }
    public String getServerCode() { return serverCode; }
    public String getToken() { return token; }
    public int getSyncIntervalSeconds() { return syncIntervalSeconds; }
    public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }

    public boolean hasCredentials() {
        return !apiUrl.isEmpty()
            && !serverCode.isEmpty()
            && token.startsWith("htb_")
            && token.length() >= 32;
    }
}
