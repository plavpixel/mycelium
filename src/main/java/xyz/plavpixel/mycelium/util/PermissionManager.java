package xyz.plavpixel.mycelium.util;

import xyz.plavpixel.mycelium.db.DatabaseManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * manages per-server command permissions
 */
public class PermissionManager {
    private final DatabaseManager dbManager;
    private final Map<String, GuildPermissions> guildPermissions;

    public PermissionManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.guildPermissions = new HashMap<>();
        loadAllPermissions();
    }

    public boolean canUseUserCommand(String guildId, Member member, String commandName) {
        // TODO - implement
        return true;
    }


    public boolean canUseModCommand(String guildId, @NotNull Member member, String commandName) {
        // allow users with ADMINISTRATOR permission or MANAGE_SERVER permission
        return member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER);
    }

    public void handleConfigCommand(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createInfoEmbed("server configuration",
                "server configuration system - coming soon!\n\n" +
                        "this will allow you to customize:\n" +
                        "• command prefixes\n" +
                        "• welcome messages\n" +
                        "• log channels\n" +
                        "• auto-moderation settings");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    public void handlePermissionsCommand(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createInfoEmbed("permission management",
                "permission management system - coming soon!\n\n" +
                        "this will allow you to:\n" +
                        "• set command permissions per role\n" +
                        "• restrict commands to specific users\n" +
                        "• configure moderator access levels\n" +
                        "• audit command usage");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private void loadAllPermissions() {
        // TODO - load permissions from database
    }

    // inner classes for permission management
    private static class GuildPermissions {
        // TODO - store permissions per guild
    }

    private static class CommandPermission {
        private final String type;
        private final String targetId;

        public CommandPermission(String type, String targetId) {
            this.type = type;
            this.targetId = targetId;
        }

        public String getType() { return type; }
        public String getTargetId() { return targetId; }
    }
}