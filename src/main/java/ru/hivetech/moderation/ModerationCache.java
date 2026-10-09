package ru.hivetech.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final ConcurrentHashMap<Long, ModerationAction> actions = new ConcurrentHashMap<>();
    private volatile long revision = 0L;

    public ModerationCache(File file) {
        this.file = file;
    }

    public synchronized void load() {
        actions.clear();
        revision = 0L;

        if (!file.isFile()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            CacheState state = GSON.fromJson(reader, CacheState.class);
            if (state == null) {
                return;
            }

            revision = Math.max(0L, state.revision);
            if (state.actions != null) {
                long now = System.currentTimeMillis();
                for (ModerationAction action : state.actions) {
                    if (action != null && action.id > 0 && action.isActive(now)) {
                        actions.put(action.id, action);
                    }
                }
            }
        } catch (Exception e) {
            HiveTechModeration.LOG.error("Cannot load moderation cache {}", file, e);
        }
    }

    public synchronized void replaceSnapshot(long newRevision, List<ModerationAction> newActions) {
        actions.clear();
        long now = System.currentTimeMillis();

        if (newActions != null) {
            for (ModerationAction action : newActions) {
                if (action != null && action.id > 0 && action.isActive(now)) {
                    actions.put(action.id, action);
                }
            }
        }

        revision = Math.max(0L, newRevision);
        cleanupExpired();
        save();
    }

    public synchronized void applyChange(ModerationChange change) {
        if (change == null) {
            return;
        }

        String op = change.operation == null ? "" : change.operation.toLowerCase();

        if ("reverse".equals(op)) {
            actions.remove(change.id);
        } else if ("apply".equals(op) || "refresh".equals(op)) {
            ModerationAction action = change.toAction();
            if (action.isActive(System.currentTimeMillis())) {
                actions.put(action.id, action);
            } else {
                actions.remove(action.id);
            }
        } else if (!"noop".equals(op)) {
            HiveTechModeration.LOG.warn("Unknown moderation change operation: {}", change.operation);
        }
    }

    public synchronized void replacePlayerActions(String uuid, List<ModerationAction> freshActions) {
        String normalized = ModerationAction.normalizeUuid(uuid);

        for (Map.Entry<Long, ModerationAction> entry : new ArrayList<>(actions.entrySet())) {
            ModerationAction action = entry.getValue();
            if (action != null && normalized.equals(action.normalizedUuid())) {
                actions.remove(entry.getKey());
            }
        }

        long now = System.currentTimeMillis();
        if (freshActions != null) {
            for (ModerationAction action : freshActions) {
                if (action != null && action.id > 0 && action.isActive(now)) {
                    actions.put(action.id, action);
                }
            }
        }

        cleanupExpired();
        save();
    }

    public ModerationAction getBan(UUID uuid, String username) {
        return firstForPlayer(uuid, username, true);
    }

    public ModerationAction getMute(UUID uuid, String username) {
        return firstForPlayer(uuid, username, false);
    }

    private ModerationAction firstForPlayer(UUID uuid, String username, boolean ban) {
        String normalized = ModerationAction.normalizeUuid(uuid.toString());
        String normalizedName = username == null ? "" : username.trim();
        long now = System.currentTimeMillis();
        ModerationAction best = null;

        for (ModerationAction action : actions.values()) {
            if (action == null || !action.isActive(now)) {
                continue;
            }

            boolean uuidMatches = normalized.equals(action.normalizedUuid());
            boolean usernameMatches = !normalizedName.isEmpty()
                && action.username != null
                && normalizedName.equalsIgnoreCase(action.username.trim());

            if (!uuidMatches && !usernameMatches) {
                continue;
            }

            if ((ban && !action.isBan()) || (!ban && !action.isMute())) {
                continue;
            }

            if (best == null || action.id > best.id) {
                best = action;
            }
        }

        return best;
    }

    public synchronized int cleanupExpired() {
        int removed = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<Long, ModerationAction> entry : new ArrayList<>(actions.entrySet())) {
            ModerationAction action = entry.getValue();
            if (action == null || !action.isActive(now)) {
                if (actions.remove(entry.getKey()) != null) {
                    removed++;
                }
            }
        }

        return removed;
    }

    public int activeCount() {
        cleanupExpired();
        return actions.size();
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = Math.max(0L, revision);
    }

    public synchronized List<ModerationAction> snapshotActions() {
        cleanupExpired();
        return new ArrayList<>(actions.values());
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Cannot create directory " + parent);
            }

            CacheState state = new CacheState();
            state.revision = revision;
            state.actions = snapshotActionsWithoutRecursion();

            File tmp = new File(file.getAbsolutePath() + ".tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }

            try {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (Exception ignored) {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (Exception e) {
            HiveTechModeration.LOG.error("Cannot save moderation cache {}", file, e);
        }
    }

    private List<ModerationAction> snapshotActionsWithoutRecursion() {
        long now = System.currentTimeMillis();
        List<ModerationAction> result = new ArrayList<>();
        for (ModerationAction action : actions.values()) {
            if (action != null && action.isActive(now)) {
                result.add(action);
            }
        }
        result.sort(Comparator.comparingLong(a -> a.id));
        return result;
    }

    private static final class CacheState {
        long revision;
        List<ModerationAction> actions = new ArrayList<>();
    }
}
