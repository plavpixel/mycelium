package xyz.plavpixel.mycelium.audio;

import xyz.plavpixel.mycelium.config.BotConfig;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.HashMap;
import java.util.Map;

/**
 * manages audio playback across multiple guilds
 */
public class AudioManager {
    private final AudioPlayerManager playerManager;
    private final Map<String, GuildAudioManager> guildAudioManagers;
    private final BotConfig config;

    public AudioManager() {
        this.config = BotConfig.getInstance();
        this.playerManager = new DefaultAudioPlayerManager();
        this.guildAudioManagers = new HashMap<>();

        // configure player manager
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    public void init(JDA jda) {
        // audio manager is ready when jda is available
    }

    public synchronized GuildAudioManager getGuildAudioManager(Guild guild) {
        String guildId = guild.getId();
        GuildAudioManager manager = guildAudioManagers.get(guildId);

        if (manager == null) {
            manager = new GuildAudioManager(playerManager, guild);
            guildAudioManagers.put(guildId, manager);
        }

        return manager;
    }

    public void closeGuildAudioManager(Guild guild) {
        GuildAudioManager manager = guildAudioManagers.remove(guild.getId());
        if (manager != null) {
            manager.close();
        }
    }

    public AudioPlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * connects to a voice channel and returns the audio manager for the guild
     */
    public GuildAudioManager connectToVoiceChannel(VoiceChannel channel) {
        GuildAudioManager manager = getGuildAudioManager(channel.getGuild());
        channel.getGuild().getAudioManager().setSendingHandler(manager.getSendHandler());
        channel.getGuild().getAudioManager().openAudioConnection(channel);
        return manager;
    }

    /**
     * disconnects from voice channel in a guild
     */
    public void disconnectFromVoiceChannel(Guild guild) {
        guild.getAudioManager().closeAudioConnection();
        closeGuildAudioManager(guild);
    }

    /**
     * checks if the bot is connected to a voice channel in the guild
     */
    public boolean isConnected(Guild guild) {
        return guild.getAudioManager().isConnected();
    }
}
