package xyz.plavpixel.mycelium.commands;

import xyz.plavpixel.mycelium.audio.AudioManager;
import xyz.plavpixel.mycelium.config.BotConfig;
import xyz.plavpixel.mycelium.db.DatabaseManager;
import xyz.plavpixel.mycelium.script.ScriptManager;
import xyz.plavpixel.mycelium.util.PermissionManager;
import xyz.plavpixel.mycelium.util.ScriptUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * handles prefix-based command parsing and execution
 */
public class CommandManager extends ListenerAdapter {
    private final BotConfig config;
    private final DatabaseManager dbManager;
    private final AudioManager audioManager;
    private final ScriptManager scriptManager;
    private final PermissionManager permissionManager;
    private final Map<String, Long> userCooldowns;
    private final ScheduledExecutorService cooldownCleaner;

    // command registries
    private final Map<String, UserCommand> userCommands;
    private final Map<String, ModCommand> modCommands;
    private final Map<String, String> commandDescriptions;

    public CommandManager(DatabaseManager dbManager, AudioManager audioManager, ScriptManager scriptManager) {
        this.config = BotConfig.getInstance();
        this.dbManager = dbManager;
        this.audioManager = audioManager;
        this.scriptManager = scriptManager;
        this.permissionManager = new PermissionManager(dbManager);
        this.userCooldowns = new ConcurrentHashMap<>();
        this.cooldownCleaner = Executors.newSingleThreadScheduledExecutor();

        this.userCommands = new HashMap<>();
        this.modCommands = new HashMap<>();
        this.commandDescriptions = new HashMap<>();

        registerBuiltInCommands();
        loadScriptCommandDescriptions();
        startCooldownCleaner();

        System.out.println("command manager initialized with prefixes: user='" + config.getUserPrefix() + "', mod='" + config.getModPrefix() + "'");
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String content = event.getMessage().getContentRaw();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        // skip dms if disabled
        if (!config.isAllowDmCommands() && event.isFromType(ChannelType.PRIVATE)) {
            return;
        }

        // check for user commands ($ prefix)
        if (content.startsWith(config.getUserPrefix())) {
            System.out.println("processing user command from " + event.getAuthor().getName() + ": " + content);
            handleUserCommand(event, content, guild, member);
            return;
        }

        // check for mod commands (# prefix)
        if (content.startsWith(config.getModPrefix())) {
            System.out.println("processing mod command from " + event.getAuthor().getName() + ": " + content);
            handleModCommand(event, content, guild, member);
        }
    }

    private void handleUserCommand(MessageReceivedEvent event, String content, Guild guild, Member member) {
        String[] parts = content.substring(config.getUserPrefix().length()).split("\\s+", 2);
        String commandName = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        // check cooldown
        if (isOnCooldown(member.getId(), commandName)) {
            System.out.println("command on cooldown: " + commandName + " for user " + member.getId());
            return;
        }

        UserCommand command = userCommands.get(commandName);
        if (command != null) {
            // check permissions
            if (permissionManager.canUseUserCommand(guild.getId(), member, commandName)) {
                System.out.println("executing built-in user command: " + commandName);
                command.execute(event, args);
                applyCooldown(member.getId(), commandName);
            } else {
                System.out.println("permission denied for user command: " + commandName);
                ScriptUtils utils = new ScriptUtils();
                EmbedBuilder embed = utils.createErrorEmbed("permission denied", "you don't have permission to use this command.");
                event.getMessage().replyEmbeds(embed.build()).queue();
            }
        } else {
            // fallback to script handler
            System.out.println("delegating to script manager for user command: " + commandName);
            scriptManager.handleUserCommand(event, commandName, args);
        }
    }

    private void handleModCommand(MessageReceivedEvent event, String content, Guild guild, Member member) {
        String[] parts = content.substring(config.getModPrefix().length()).split("\\s+", 2);
        String commandName = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        // check cooldown
        if (isOnCooldown(member.getId(), commandName)) {
            System.out.println("command on cooldown: " + commandName + " for user " + member.getId());
            return;
        }

        ModCommand command = modCommands.get(commandName);
        if (command != null) {
            // check permissions (except for reload which is owner-only)
            if (commandName.equals("reload")) {
                if (!config.isUserOwner(event.getAuthor().getIdLong())) {
                    System.out.println("reload command attempted by non-owner: " + event.getAuthor().getId());
                    ScriptUtils utils = new ScriptUtils();
                    EmbedBuilder embed = utils.createErrorEmbed("permission denied", "only bot owners can reload scripts.");
                    event.getMessage().replyEmbeds(embed.build()).queue();
                    return;
                }
            } else if (!permissionManager.canUseModCommand(guild.getId(), member, commandName)) {
                System.out.println("permission denied for mod command: " + commandName);
                ScriptUtils utils = new ScriptUtils();
                EmbedBuilder embed = utils.createErrorEmbed("permission denied", "you don't have permission to use this command.");
                event.getMessage().replyEmbeds(embed.build()).queue();
                return;
            }

            System.out.println("executing built-in mod command: " + commandName);
            command.execute(event, args);
            applyCooldown(member.getId(), commandName);
        } else {
            // fallback to script handler
            System.out.println("delegating to script manager for mod command: " + commandName);
            scriptManager.handleModCommand(event, commandName, args);
        }
    }

    private boolean isOnCooldown(String userId, String commandName) {
        String key = userId + ":" + commandName;
        Long lastUsed = userCooldowns.get(key);
        if (lastUsed == null) return false;

        long cooldownMs = config.getCommandCooldownSeconds() * 1000L;
        return System.currentTimeMillis() - lastUsed < cooldownMs;
    }

    private void applyCooldown(String userId, String commandName) {
        String key = userId + ":" + commandName;
        userCooldowns.put(key, System.currentTimeMillis());
    }

    private void startCooldownCleaner() {
        cooldownCleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long cooldownMs = config.getCommandCooldownSeconds() * 1000L;

            userCooldowns.entrySet().removeIf(entry ->
                    now - entry.getValue() > cooldownMs
            );
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * load command descriptions from script metadata
     */
    private void loadScriptCommandDescriptions() {
        File scriptsDir = new File(config.getScriptsDirectory());
        File[] files = scriptsDir.listFiles((dir, name) -> name.endsWith(".js"));

        if (files == null) return;

        Pattern pattern = Pattern.compile("/\\*\\*([\\s\\S]*?)\\*/");

        for (File file : files) {
            try {
                String content = Files.readString(file.toPath());
                Matcher matcher = pattern.matcher(content);

                if (matcher.find()) {
                    String metadata = matcher.group(1).trim();
                    parseCommandDescriptions(metadata);
                }
            } catch (IOException e) {
                System.err.println("failed to read script file for descriptions: " + file.getName());
            }
        }
    }

    private void parseCommandDescriptions(String metadata) {
        try {
            // simple json array parsing
            metadata = metadata.trim();
            if (!metadata.startsWith("[") || !metadata.endsWith("]")) return;

            // basic json parsing - for simplicity, we'll use string operations
            String[] commands = metadata.substring(1, metadata.length() - 1).split("\\},\\s*\\{");

            for (String command : commands) {
                command = command.trim();
                if (command.startsWith("{")) command = command.substring(1);
                if (command.endsWith("}")) command = command.substring(0, command.length() - 1);

                // parse key-value pairs
                String[] pairs = command.split(",");
                String name = null;
                String prefix = null;
                String description = null;

                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length != 2) continue;

                    String key = keyValue[0].trim().replace("\"", "").replace("'", "");
                    String value = keyValue[1].trim().replace("\"", "").replace("'", "");

                    switch (key) {
                        case "name":
                            name = value;
                            break;
                        case "prefix":
                            prefix = value;
                            break;
                        case "description":
                            description = value;
                            break;
                    }
                }

                if (name != null && prefix != null && description != null) {
                    String fullCommand = (prefix.equals("user") ? config.getUserPrefix() : config.getModPrefix()) + name;
                    commandDescriptions.put(fullCommand, description);
                }
            }
        } catch (Exception e) {
            System.err.println("error parsing command descriptions: " + e.getMessage());
        }
    }

    /**
     * register built-in java commands
     */
    private void registerBuiltInCommands() {
        // user commands
        userCommands.put("ping", this::handlePing);
        userCommands.put("help", this::handleHelp);
        userCommands.put("play", this::handlePlay);
        userCommands.put("skip", this::handleSkip);
        userCommands.put("queue", this::handleQueue);
        userCommands.put("volume", this::handleVolume);
        userCommands.put("nowplaying", this::handleNowPlaying);

        // mod commands
        modCommands.put("config", this::handleConfig);
        modCommands.put("permissions", this::handlePermissions);
        modCommands.put("clean", this::handleClean);
        modCommands.put("reload", this::handleReload);

        // add built-in command descriptions
        commandDescriptions.put(config.getUserPrefix() + "ping", "checks bot latency and response time");
        commandDescriptions.put(config.getUserPrefix() + "help", "shows this help message with all available commands");
        commandDescriptions.put(config.getUserPrefix() + "play", "plays audio from youtube or other sources");
        commandDescriptions.put(config.getUserPrefix() + "skip", "skips the current track");
        commandDescriptions.put(config.getUserPrefix() + "queue", "shows the current playback queue");
        commandDescriptions.put(config.getUserPrefix() + "volume", "adjusts the playback volume");
        commandDescriptions.put(config.getUserPrefix() + "nowplaying", "shows the currently playing track");

        commandDescriptions.put(config.getModPrefix() + "config", "server configuration management");
        commandDescriptions.put(config.getModPrefix() + "permissions", "manage command permissions");
        commandDescriptions.put(config.getModPrefix() + "clean", "deletes a number of messages");
        commandDescriptions.put(config.getModPrefix() + "reload", "reloads all scripts (owner only)");
    }

    // user command implementations
    private void handlePing(MessageReceivedEvent event, String args) {
        long startTime = System.currentTimeMillis();
        long gatewayPing = event.getJDA().getGatewayPing();

        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createEmbed("ping", "calculating response time...", utils.INFO_COLOR);

        event.getMessage().replyEmbeds(embed.build()).queue(response -> {
            long responseTime = System.currentTimeMillis() - startTime;
            EmbedBuilder updatedEmbed = utils.createEmbed("pong! 🏓",
                    "**gateway:** " + gatewayPing + "ms\n**response:** " + responseTime + "ms",
                    utils.SUCCESS_COLOR);
            utils.addMessageFooter(updatedEmbed, event);
            response.editMessageEmbeds(updatedEmbed.build()).queue();
        });
    }

    private void handleHelp(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createEmbed("mycelium bot help",
                "here are all available commands. use `" + config.getUserPrefix() + "command` or `" + config.getModPrefix() + "command`",
                utils.INFO_COLOR);

        // group commands by prefix
        Map<String, List<String>> userCommands = new HashMap<>();
        Map<String, List<String>> modCommands = new HashMap<>();

        for (Map.Entry<String, String> entry : commandDescriptions.entrySet()) {
            String command = entry.getKey();
            String description = entry.getValue();

            if (command.startsWith(config.getUserPrefix())) {
                String cmdName = command.substring(config.getUserPrefix().length());
                userCommands.computeIfAbsent(cmdName, k -> new ArrayList<>()).add(description);
            } else if (command.startsWith(config.getModPrefix())) {
                String cmdName = command.substring(config.getModPrefix().length());
                modCommands.computeIfAbsent(cmdName, k -> new ArrayList<>()).add(description);
            }
        }

        // build user commands field
        if (!userCommands.isEmpty()) {
            StringBuilder userCommandsText = new StringBuilder();
            userCommands.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        userCommandsText.append("`").append(config.getUserPrefix()).append(entry.getKey()).append("` - ");
                        // if there are multiple descriptions, use the first one
                        userCommandsText.append(entry.getValue().get(0)).append("\n");
                    });
            embed.addField("user commands", userCommandsText.toString(), false);
        }

        // build mod commands field
        if (!modCommands.isEmpty()) {
            StringBuilder modCommandsText = new StringBuilder();
            modCommands.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        modCommandsText.append("`").append(config.getModPrefix()).append(entry.getKey()).append("` - ");
                        // if there are multiple descriptions, use the first one
                        modCommandsText.append(entry.getValue().get(0)).append("\n");
                    });
            embed.addField("moderation commands", modCommandsText.toString(), false);
        }

        embed.addField("need more help?",
                "• use `" + config.getUserPrefix() + "command` to execute a command\n" +
                        "• use `" + config.getModPrefix() + "command` for moderation\n" +
                        "• check script files for command-specific usage",
                false);

        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private void handlePlay(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();

        if (args.isEmpty()) {
            EmbedBuilder embed = utils.createErrorEmbed("usage", "`" + config.getUserPrefix() + "play <url or search query>`");
            event.getMessage().replyEmbeds(embed.build()).queue();
            return;
        }

        EmbedBuilder embed = utils.createInfoEmbed("music player", "searching for: " + args);
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private void handleSkip(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createSuccessEmbed("music player", "skipping current track...");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private void handleQueue(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createInfoEmbed("music queue", "queue display would appear here");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private void handleVolume(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createInfoEmbed("volume control", "volume adjustment would appear here");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private void handleNowPlaying(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();
        EmbedBuilder embed = utils.createInfoEmbed("now playing", "current track info would appear here");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    // mod command implementations
    private void handleConfig(MessageReceivedEvent event, String args) {
        permissionManager.handleConfigCommand(event, args);
    }

    private void handlePermissions(MessageReceivedEvent event, String args) {
        permissionManager.handlePermissionsCommand(event, args);
    }

    private void handleClean(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();

        try {
            int amount = Integer.parseInt(args);
            if (amount < 1 || amount > 100) {
                EmbedBuilder embed = utils.createErrorEmbed("invalid amount", "please specify a number between 1 and 100.");
                event.getMessage().replyEmbeds(embed.build()).queue();
                return;
            }

            event.getChannel().getIterableHistory()
                    .takeAsync(amount + 1) // +1 to include the command message
                    .thenAccept(messages -> {
                        int deletedCount = 0;
                        for (var message : messages) {
                            if (!message.isPinned()) {
                                message.delete().queue();
                                deletedCount++;
                            }
                        }

                        EmbedBuilder successEmbed = utils.createSuccessEmbed("cleanup complete", "deleted " + deletedCount + " messages.");
                        event.getChannel().sendMessageEmbeds(successEmbed.build()).queue(confirmMsg ->
                                confirmMsg.delete().queueAfter(3, TimeUnit.SECONDS)
                        );
                    });
        } catch (NumberFormatException e) {
            EmbedBuilder embed = utils.createErrorEmbed("usage", "`" + config.getModPrefix() + "clean <number of messages>`");
            event.getMessage().replyEmbeds(embed.build()).queue();
        }
    }

    private void handleReload(MessageReceivedEvent event, String args) {
        ScriptUtils utils = new ScriptUtils();

        // owner check is now done in handleModCommand
        scriptManager.loadScripts();
        loadScriptCommandDescriptions(); // reload descriptions too

        EmbedBuilder embed = utils.createSuccessEmbed("reload complete", "all scripts have been reloaded successfully.");
        utils.addMessageFooter(embed, event);
        event.getMessage().replyEmbeds(embed.build()).queue();

        System.out.println("scripts reloaded by owner: " + event.getAuthor().getName());
    }

    public void registerUserCommand(String name, UserCommand command) {
        userCommands.put(name.toLowerCase(), command);
    }

    public void registerModCommand(String name, ModCommand command) {
        modCommands.put(name.toLowerCase(), command);
    }

    public void shutdown() {
        cooldownCleaner.shutdown();
    }
}
