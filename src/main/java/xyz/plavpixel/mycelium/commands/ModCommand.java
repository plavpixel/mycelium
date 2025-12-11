package xyz.plavpixel.mycelium.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * functional interface for moderation commands
 */
@FunctionalInterface
public interface ModCommand {
    void execute(MessageReceivedEvent event, String args);
}
