package xyz.plavpixel.mycelium.events;

import xyz.plavpixel.mycelium.script.ScriptManager;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * handles discord events and delegates to script handlers
 */
public class EventManager extends ListenerAdapter {
    private final ScriptManager scriptManager;

    public EventManager(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    private void handleGenericEvent(String eventType, GenericEvent event) {
        if (scriptManager.hasEventHandler(eventType)) {
            new Thread(() -> scriptManager.executeEventHandler(eventType, event)).start();
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        handleGenericEvent("READY", event);
        System.out.println("bot is ready and connected to " + event.getGuildAvailableCount() + " guilds");
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        handleGenericEvent("MEMBER_JOIN", event);
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        handleGenericEvent("MEMBER_LEAVE", event);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        handleGenericEvent("MESSAGE_RECEIVED", event);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        handleGenericEvent("VOICE_UPDATE", event);
    }
}