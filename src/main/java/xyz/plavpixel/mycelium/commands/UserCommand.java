package xyz.plavpixel.mycelium.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * functional interface for user commands
 */
@FunctionalInterface
public interface UserCommand {
    void execute(MessageReceivedEvent event, String args);
}
