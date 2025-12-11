package xyz.plavpixel.mycelium.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.Color;
import java.time.Instant;

/**
 * utility class for script helper methods
 */
public class ScriptUtils {
    public static final Color SUCCESS_COLOR = new Color(0x2ECC71);
    public static final Color ERROR_COLOR = new Color(0xE74C3C);
    public static final Color INFO_COLOR = new Color(0x3498DB);
    public static final Color WARNING_COLOR = new Color(0xF39C12);

    public EmbedBuilder createEmbed(String title, String description, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now());
    }

    public EmbedBuilder createSuccessEmbed(String title, String description) {
        return createEmbed(title, description, SUCCESS_COLOR);
    }

    public EmbedBuilder createErrorEmbed(String title, String description) {
        return createEmbed(title, description, ERROR_COLOR);
    }

    public EmbedBuilder createInfoEmbed(String title, String description) {
        return createEmbed(title, description, INFO_COLOR);
    }

    public EmbedBuilder createWarningEmbed(String title, String description) {
        return createEmbed(title, description, WARNING_COLOR);
    }

    public EmbedBuilder addDefaultFooter(EmbedBuilder embedBuilder, SlashCommandInteractionEvent event) {
        JDA.ShardInfo shardInfo = event.getJDA().getShardInfo();
        long gatewayPing = event.getJDA().getGatewayPing();
        String footerText = String.format("ping: %dms | shard: [%d/%d] | requested by: %s",
                gatewayPing,
                shardInfo.getShardId() + 1,
                shardInfo.getShardTotal(),
                event.getUser().getName());

        embedBuilder.setFooter(footerText, event.getUser().getEffectiveAvatarUrl());
        return embedBuilder;
    }

    public EmbedBuilder addMessageFooter(EmbedBuilder embedBuilder, MessageReceivedEvent event) {
        JDA.ShardInfo shardInfo = event.getJDA().getShardInfo();
        long gatewayPing = event.getJDA().getGatewayPing();
        String footerText = String.format("ping: %dms | shard: [%d/%d] | requested by: %s",
                gatewayPing,
                shardInfo.getShardId() + 1,
                shardInfo.getShardTotal(),
                event.getAuthor().getName());

        embedBuilder.setFooter(footerText, event.getAuthor().getEffectiveAvatarUrl());
        return embedBuilder;
    }

    public String formatUserMention(String userId) {
        return "<@" + userId + ">";
    }

    public String formatRoleMention(String roleId) {
        return "<@&" + roleId + ">";
    }

    public String formatChannelMention(String channelId) {
        return "<#" + channelId + ">";
    }

    public String formatTimestamp(long epochSeconds, String style) {
        return "<t:" + epochSeconds + ":" + style + ">";
    }

    public String formatRelativeTime(long epochSeconds) {
        return formatTimestamp(epochSeconds, "R");
    }
}