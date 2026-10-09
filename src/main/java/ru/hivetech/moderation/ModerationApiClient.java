package ru.hivetech.moderation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ModerationApiClient {
    private static final Gson GSON = new Gson();

    private final ModerationConfig config;

    public ModerationApiClient(ModerationConfig config) {
        this.config = config;
    }

    public ApiResponses.SnapshotResponse snapshot() throws IOException {
        return request(
            "GET",
            "/moderation/snapshot",
            null,
            ApiResponses.SnapshotResponse.class
        );
    }

    public ApiResponses.ChangesResponse changes(long after) throws IOException {
        return request(
            "GET",
            "/moderation/changes?after=" + Math.max(0L, after) + "&limit=200",
            null,
            ApiResponses.ChangesResponse.class
        );
    }

    public ApiResponses.PlayerResponse player(String uuid) throws IOException {
        return request(
            "GET",
            "/moderation/player/" + uuid,
            null,
            ApiResponses.PlayerResponse.class
        );
    }

    public ApiResponses.HeartbeatResponse heartbeat(
        long revision,
        int cachedActions,
        int onlinePlayers,
        boolean syncOk,
        String lastError
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("bridge_version", HiveTechModeration.VERSION);
        body.addProperty("minecraft_version", "1.12.2");
        body.addProperty("forge_version", net.minecraftforge.common.ForgeVersion.getVersion());
        body.addProperty("revision", Math.max(0L, revision));
        body.addProperty("cached_actions", Math.max(0, cachedActions));
        body.addProperty("online_players", Math.max(0, onlinePlayers));
        body.addProperty("sync_ok", syncOk);
        body.addProperty("last_error", lastError == null ? "" : lastError);

        return request(
            "POST",
            "/bridge/heartbeat",
            GSON.toJson(body),
            ApiResponses.HeartbeatResponse.class
        );
    }

    private <T> T request(String method, String path, String body, Class<T> type) throws IOException {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(config.getApiUrl() + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(config.getConnectTimeoutSeconds() * 1000);
            connection.setReadTimeout(config.getReadTimeoutSeconds() * 1000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HiveTechModeration/" + HiveTechModeration.VERSION);
            connection.setRequestProperty("Authorization", "Bearer " + config.getToken());
            connection.setRequestProperty("X-HiveTech-Server", config.getServerCode());

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

            String response = readLimited(stream, 1024 * 1024);

            if (status < 200 || status >= 300) {
                throw new ApiException(status, sanitizeError(response));
            }

            T parsed = GSON.fromJson(response, type);
            if (parsed == null) {
                throw new IOException("Empty/invalid JSON response");
            }
            return parsed;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readLimited(InputStream stream, int maxBytes) throws IOException {
        if (stream == null) {
            return "";
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;

        while ((read = stream.read(buffer)) != -1) {
            if (total + read > maxBytes) {
                int allowed = maxBytes - total;
                if (allowed > 0) {
                    out.write(buffer, 0, allowed);
                }
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }

        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String sanitizeError(String response) {
        if (response == null) {
            return "";
        }
        String clean = response.replaceAll("[\\r\\n]+", " ").trim();
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    public static final class ApiException extends IOException {
        private final int statusCode;

        public ApiException(int statusCode, String message) {
            super("HTTP " + statusCode + (message.isEmpty() ? "" : ": " + message));
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
