package ru.hivetech.moderation;

import java.util.ArrayList;
import java.util.List;

final class ApiResponses {
    private ApiResponses() {}

    static final class ServerInfo {
        int id;
        String code;
    }

    static class BaseResponse {
        int api_version;
        ServerInfo server;
        String generated_at;
        String error;
    }

    static final class SnapshotResponse extends BaseResponse {
        long revision;
        List<ModerationAction> actions = new ArrayList<>();
    }

    static final class ChangesResponse extends BaseResponse {
        long revision;
        long current_revision;
        boolean has_more;
        List<ModerationChange> changes = new ArrayList<>();
    }

    static final class PlayerResponse extends BaseResponse {
        long revision;
        List<ModerationAction> actions = new ArrayList<>();
    }

    static final class HeartbeatResponse extends BaseResponse {
        boolean accepted;
        long current_revision;
    }
}
