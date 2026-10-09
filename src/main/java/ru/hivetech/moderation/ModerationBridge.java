package ru.hivetech.moderation;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModerationBridge {
    private final MinecraftServer server;
    private final ModerationConfig config;
    private final ModerationCache cache;
    private final ModerationApiClient api;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    private volatile boolean snapshotLoaded = false;
    private volatile long lastSyncSuccessAt = 0L;
    private volatile String lastError = "";
    private volatile int lastHttpStatus = 0;

    public ModerationBridge(MinecraftServer server, ModerationConfig config, ModerationCache cache) {
        this.server = server;
        this.config = config;
        this.cache = cache;
        this.api = new ModerationApiClient(config);
        this.executor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private int index = 0;

            @Override
            public synchronized Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "HiveTechModeration-" + (++index));
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        cache.cleanupExpired();
        cache.save();

        if (!config.isEnabled()) {
            HiveTechModeration.LOG.warn("HiveTech Moderation Bridge is disabled in config");
            return;
        }

        if (!config.hasCredentials()) {
            lastError = "Bridge token/serverCode is not configured";
            HiveTechModeration.LOG.error(
                "HiveTech Moderation API credentials are missing. Cached punishments will still be enforced."
            );
            return;
        }

        executor.scheduleWithFixedDelay(
            this::safeSync,
            0,
            config.getSyncIntervalSeconds(),
            TimeUnit.SECONDS
        );

        executor.scheduleWithFixedDelay(
            this::safeHeartbeat,
            5,
            config.getHeartbeatIntervalSeconds(),
            TimeUnit.SECONDS
        );

        HiveTechModeration.LOG.info(
            "HiveTech Moderation Bridge started: api={}, serverCode={}, revision={}",
            config.getApiUrl(),
            config.getServerCode(),
            cache.getRevision()
        );
    }

    public void stop() {
        cache.save();
        executor.shutdownNow();
        started.set(false);
        HiveTechModeration.LOG.info("HiveTech Moderation Bridge stopped");
    }

    public void syncNowAsync() {
        if (!config.isEnabled() || !config.hasCredentials()) {
            return;
        }
        executor.submit(this::safeSync);
    }

    public void onPlayerLogin(EntityPlayerMP player) {
        ModerationAction cachedBan = cache.getBan(player.getUniqueID(), player.getName());
        if (cachedBan != null) {
            disconnect(player, cachedBan);
            return;
        }

        if (!config.isEnabled() || !config.hasCredentials()) {
            return;
        }

        final UUID uuid = player.getUniqueID();
        executor.submit(() -> liveCheckPlayer(uuid));
    }

    public ModerationAction getMute(EntityPlayerMP player) {
        return cache.getMute(player.getUniqueID(), player.getName());
    }

    private void liveCheckPlayer(UUID uuid) {
        try {
            ApiResponses.PlayerResponse response = api.player(uuid.toString());
            cache.replacePlayerActions(uuid.toString(), response.actions);
            clearError();

            ModerationAction ban = cache.getBan(uuid, null);
            if (ban != null) {
                server.addScheduledTask(() -> {
                    EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(uuid);
                    if (online != null) {
                        disconnect(online, ban);
                    }
                });
            }
        } catch (Exception e) {
            recordError("player lookup", e);
        }
    }

    private void safeSync() {
        if (!syncRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!snapshotLoaded) {
                loadSnapshot();
            } else {
                loadChanges();
            }

            cache.cleanupExpired();
            cache.save();
            lastSyncSuccessAt = System.currentTimeMillis();
            clearError();
        } catch (Exception e) {
            recordError("sync", e);
        } finally {
            syncRunning.set(false);
        }
    }

    private void loadSnapshot() throws Exception {
        ApiResponses.SnapshotResponse response = api.snapshot();
        cache.replaceSnapshot(response.revision, response.actions);
        snapshotLoaded = true;

        HiveTechModeration.LOG.info(
            "Moderation snapshot loaded: revision={}, actions={}",
            cache.getRevision(),
            cache.activeCount()
        );

        enforceOnlineBans();
    }

    private void loadChanges() throws Exception {
        int pages = 0;
        boolean more;

        do {
            ApiResponses.ChangesResponse response = api.changes(cache.getRevision());
            List<ModerationChange> changes = response.changes == null
                ? Collections.emptyList()
                : response.changes;

            List<ModerationAction> newlyAppliedBans = new ArrayList<>();

            for (ModerationChange change : changes) {
                if (change == null) {
                    continue;
                }

                cache.applyChange(change);

                if (
                    "apply".equalsIgnoreCase(change.operation)
                    && change.isBan()
                    && change.isActive(System.currentTimeMillis())
                ) {
                    newlyAppliedBans.add(change.toAction());
                }
            }

            cache.setRevision(response.revision);
            more = response.has_more;
            pages++;

            for (ModerationAction ban : newlyAppliedBans) {
                kickIfOnline(ban);
            }

            if (pages >= 20 && more) {
                throw new IllegalStateException("Too many moderation change pages in one sync");
            }
        } while (more);
    }

    private void enforceOnlineBans() {
        server.addScheduledTask(() -> {
            for (EntityPlayerMP player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                ModerationAction ban = cache.getBan(player.getUniqueID(), player.getName());
                if (ban != null) {
                    disconnect(player, ban);
                }
            }
        });
    }

    private void kickIfOnline(ModerationAction ban) {
        String normalized = ban.normalizedUuid();
        server.addScheduledTask(() -> {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                boolean uuidMatches = normalized.equals(
                    ModerationAction.normalizeUuid(player.getUniqueID().toString())
                );
                boolean usernameMatches = ban.username != null
                    && ban.username.equalsIgnoreCase(player.getName());

                if (uuidMatches || usernameMatches) {
                    disconnect(player, ban);
                    break;
                }
            }
        });
    }

    private void safeHeartbeat() {
        try {
            api.heartbeat(
                cache.getRevision(),
                cache.activeCount(),
                server.getCurrentPlayerCount(),
                lastError.isEmpty(),
                lastError
            );
            clearHttpStatus();
        } catch (Exception e) {
            recordError("heartbeat", e);
        }
    }

    public void disconnect(EntityPlayerMP player, ModerationAction ban) {
        if (player == null || ban == null || player.connection == null) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("§c§lДоступ к серверу ограничен§r\n\n");
        message.append("§7Причина: §f").append(safeText(ban.reason)).append("\n");

        Long expires = ban.expiresAtMillis();
        if (expires == null) {
            message.append("§7Срок: §fнавсегда\n");
        } else {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            message.append("§7До: §f").append(format.format(new Date(expires))).append("\n");
        }

        message.append("§7ID наказания: §f#").append(ban.id).append("\n");
        message.append("§7Обжалование: §fhivegrid.online/support");

        player.connection.disconnect(new TextComponentString(message.toString()));
    }

    public String formatMuteMessage(ModerationAction mute) {
        StringBuilder message = new StringBuilder();
        message.append("§cЧат временно недоступен. §7Причина: §f")
            .append(safeText(mute.reason));

        Long expires = mute.expiresAtMillis();
        if (expires == null) {
            message.append(" §7Срок: §fнавсегда");
        } else {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            message.append(" §7До: §f").append(format.format(new Date(expires)));
        }

        message.append(" §8[#").append(mute.id).append("]");
        return message.toString();
    }

    private static String safeText(String value) {
        if (value == null) {
            return "Не указана";
        }
        String result = value.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() > 180 ? result.substring(0, 180) : result;
    }

    private void recordError(String operation, Exception e) {
        if (e instanceof ModerationApiClient.ApiException) {
            lastHttpStatus = ((ModerationApiClient.ApiException) e).getStatusCode();
        } else {
            lastHttpStatus = 0;
        }

        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        lastError = operation + ": " + message;
        if (lastError.length() > 1000) {
            lastError = lastError.substring(0, 1000);
        }

        HiveTechModeration.LOG.warn("Moderation {} failed: {}", operation, message);
    }

    private void clearError() {
        lastError = "";
        clearHttpStatus();
    }

    private void clearHttpStatus() {
        lastHttpStatus = 0;
    }

    public boolean isStarted() { return started.get(); }
    public long getLastSyncSuccessAt() { return lastSyncSuccessAt; }
    public String getLastError() { return lastError; }
    public int getLastHttpStatus() { return lastHttpStatus; }
    public long getRevision() { return cache.getRevision(); }
    public int getCachedActions() { return cache.activeCount(); }
    public boolean isSnapshotLoaded() { return snapshotLoaded; }
}
