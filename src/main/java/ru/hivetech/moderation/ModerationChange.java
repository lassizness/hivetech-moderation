package ru.hivetech.moderation;

public final class ModerationChange extends ModerationAction {
    public long revision;
    public String operation;
    public String event_type;

    public ModerationAction toAction() {
        ModerationAction action = new ModerationAction();
        action.id = id;
        action.uuid = uuid;
        action.username = username;
        action.type = type;
        action.scope = scope;
        action.server_code = server_code;
        action.reason = reason;
        action.starts_at = starts_at;
        action.expires_at = expires_at;
        action.status = status;
        return action;
    }
}
