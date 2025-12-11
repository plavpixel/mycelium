package xyz.plavpixel.mycelium.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * manages audio playback for a single guild
 */
public class GuildAudioManager extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final Guild guild;

    public GuildAudioManager(AudioPlayerManager playerManager, Guild guild) {
        this.player = playerManager.createPlayer();
        this.queue = new LinkedBlockingQueue<>();
        this.guild = guild;
        this.player.addListener(this);
    }

    /**
     * starts playback of the next track in the queue
     */
    private void playNextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            player.playTrack(nextTrack);
        } else {
            guild.getAudioManager().closeAudioConnection();
        }
    }

    /**
     * queues a track for playback
     */
    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    /**
     * skips the current track
     */
    public void skipTrack() {
        player.stopTrack();
        playNextTrack();
    }

    /**
     * clears the queue
     */
    public void clearQueue() {
        queue.clear();
    }

    /**
     * gets the current track
     */
    public AudioTrack getCurrentTrack() {
        return player.getPlayingTrack();
    }

    /**
     * gets the queue
     */
    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    /**
     * checks if a track is currently playing
     */
    public boolean isPlaying() {
        return player.getPlayingTrack() != null;
    }

    /**
     * sets the player volume
     */
    public void setVolume(int volume) {
        player.setVolume(volume);
    }

    /**
     * gets the audio player send handler for jda
     */
    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            playNextTrack();
        }
    }

    /**
     * clean up resources
     */
    public void close() {
        player.destroy();
        queue.clear();
    }
}
