package ru.hivetech.moderation;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class ModerationCommand extends CommandBase {
    @Override
    public String getName() {
        return "htmod";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/htmod <status|sync>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        ModerationBridge bridge = HiveTechModeration.getBridge();
        if (bridge == null) {
            sender.sendMessage(new TextComponentString("§cHiveTech Moderation Bridge не запущен."));
            return;
        }

        String action = args.length == 0 ? "status" : args[0].toLowerCase();

        if ("sync".equals(action)) {
            bridge.syncNowAsync();
            sender.sendMessage(new TextComponentString("§aПринудительная синхронизация поставлена в очередь."));
            return;
        }

        if (!"status".equals(action)) {
            throw new CommandException(getUsage(sender));
        }

        ModerationConfig config = HiveTechModeration.getConfig();
        sender.sendMessage(new TextComponentString("§6HiveTech Moderation " + HiveTechModeration.VERSION));
        sender.sendMessage(new TextComponentString("§7API: §f" + config.getApiUrl()));
        sender.sendMessage(new TextComponentString("§7Server code: §f" + config.getServerCode()));
        sender.sendMessage(new TextComponentString("§7Credentials: §f" + (config.hasCredentials() ? "configured" : "MISSING")));
        sender.sendMessage(new TextComponentString("§7Revision: §f" + bridge.getRevision()));
        sender.sendMessage(new TextComponentString("§7Cache: §f" + bridge.getCachedActions()));
        sender.sendMessage(new TextComponentString("§7Snapshot: §f" + (bridge.isSnapshotLoaded() ? "loaded" : "not loaded")));
        sender.sendMessage(new TextComponentString("§7Last sync: §f" + formatTime(bridge.getLastSyncSuccessAt())));
        sender.sendMessage(new TextComponentString("§7HTTP status: §f" + (bridge.getLastHttpStatus() == 0 ? "—" : bridge.getLastHttpStatus())));
        sender.sendMessage(new TextComponentString("§7Last error: §f" + (bridge.getLastError().isEmpty() ? "—" : bridge.getLastError())));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "sync");
        }
        return Collections.emptyList();
    }

    private static String formatTime(long millis) {
        if (millis <= 0L) {
            return "—";
        }
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(millis));
    }
}
