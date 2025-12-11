package xyz.plavpixel.mycelium.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.plavpixel.mycelium.audio.AudioManager;
import xyz.plavpixel.mycelium.config.BotConfig;
import xyz.plavpixel.mycelium.db.DatabaseManager;
import xyz.plavpixel.mycelium.util.HttpUtils;
import xyz.plavpixel.mycelium.util.Scheduler;
import xyz.plavpixel.mycelium.util.ScriptUtils;
import xyz.plavpixel.mycelium.util.TimeUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * manages javascript script loading and execution
 */
public class ScriptManager {
    private final DatabaseManager dbManager;
    private final AudioManager audioManager;
    private final HttpUtils httpUtils;
    private Scheduler scheduler;
    private final TimeUtils timeUtils;
    private Context context;
    private final Map<String, List<String>> eventHandlers;
    private final Map<String, List<String>> prefixCommandHandlers;
    private final File scriptsDirectory;
    private final BotConfig config;
    private final ObjectMapper jsonMapper;
    private final ExecutorService scriptExecutor;

    public ScriptManager(DatabaseManager dbManager, AudioManager audioManager) {
        this.dbManager = dbManager;
        this.audioManager = audioManager;
        this.httpUtils = new HttpUtils();
        this.timeUtils = new TimeUtils();
        this.config = BotConfig.getInstance();
        this.scriptsDirectory = new File(config.getScriptsDirectory());
        this.jsonMapper = new ObjectMapper();
        this.eventHandlers = new ConcurrentHashMap<>();
        this.prefixCommandHandlers = new ConcurrentHashMap<>();
        this.scriptExecutor = Executors.newSingleThreadExecutor();

        System.out.println("script manager initialized with scripts directory: " + scriptsDirectory.getAbsolutePath());
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void loadScripts() {
        // submit to single thread to avoid threading issues
        Future<?> future = scriptExecutor.submit(() -> {
            eventHandlers.clear();
            prefixCommandHandlers.clear();
            initializeContext();

            File[] files = scriptsDirectory.listFiles((dir, name) -> name.endsWith(".js"));
            if (files == null) {
                System.err.println("error: could not find scripts directory: " + scriptsDirectory.getPath());
                return;
            }

            System.out.println("loading scripts from: " + scriptsDirectory.getAbsolutePath());
            System.out.println("found " + files.length + " script files");

            Pattern pattern = Pattern.compile("/\\*\\*([\\s\\S]*?)\\*/");

            for (File file : files) {
                String scriptName = file.getName();
                if (config.getDisabledScripts().contains(scriptName)) {
                    System.out.println("skipping disabled script: " + scriptName);
                    continue;
                }

                try {
                    String scriptContent = Files.readString(file.toPath());
                    Matcher matcher = pattern.matcher(scriptContent);

                    if (matcher.find()) {
                        String metadataBlock = matcher.group(1).trim();
                        parseMetadata(metadataBlock, scriptName);
                    }

                    context.eval(Source.newBuilder("js", scriptContent, scriptName).build());
                    System.out.println("✓ loaded script: " + scriptName);
                } catch (IOException | PolyglotException e) {
                    System.err.println("✗ failed to load script: " + scriptName + " - " + e.getMessage());
                    if (config.isDebugMode()) e.printStackTrace();
                }
            }

            System.out.println("script loading complete. registered " + prefixCommandHandlers.size() + " prefix command handlers");
        });

        try {
            future.get(30, TimeUnit.SECONDS); // wait for loading to complete
        } catch (TimeoutException e) {
            System.err.println("script loading timed out");
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("script loading failed: " + e.getMessage());
        }
    }

    private void parseMetadata(String json, String scriptName) {
        try {
            List<Map<String, Object>> definitions = jsonMapper.readValue(json, new TypeReference<>() {});
            System.out.println("parsing metadata from " + scriptName + " - found " + definitions.size() + " definitions");

            for (Map<String, Object> def : definitions) {
                // event handlers
                if (def.containsKey("event") && def.containsKey("handler")) {
                    String eventType = ((String) def.get("event")).toUpperCase(Locale.ROOT);
                    String handlerName = (String) def.get("handler");
                    eventHandlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handlerName);
                    System.out.println("  - registered event handler: " + eventType + " -> " + handlerName);
                }
                // prefix commands
                else if (def.containsKey("prefix") && def.containsKey("handler") && def.containsKey("name")) {
                    String prefixType = (String) def.get("prefix");
                    String commandName = (String) def.get("name");
                    String handlerName = (String) def.get("handler");

                    String key = prefixType + ":" + commandName;
                    prefixCommandHandlers.computeIfAbsent(key, k -> new ArrayList<>()).add(handlerName);
                    System.out.println("  - registered prefix command: " + prefixType + ":" + commandName + " -> " + handlerName);
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("error parsing metadata in " + scriptName + ": " + e.getMessage());
        }
    }

    // handle user prefix commands from scripts
    public void handleUserCommand(MessageReceivedEvent event, String commandName, String args) {
        scriptExecutor.submit(() -> {
            String key = "user:" + commandName;
            List<String> handlers = prefixCommandHandlers.get(key);

            if (handlers == null || handlers.isEmpty()) {
                System.out.println("no script handler found for user command: " + commandName);
                ScriptUtils utils = new ScriptUtils();
                EmbedBuilder embed = utils.createErrorEmbed("command not found",
                        "the command `" + commandName + "` was not found. use `$help` for available commands.");
                event.getMessage().replyEmbeds(embed.build()).queue();
                return;
            }

            System.out.println("executing user command: " + commandName + " with args: " + args);

            for (String handlerName : handlers) {
                executePrefixCommandHandler(event, handlerName, commandName, args);
            }
        });
    }

    // handle mod prefix commands from scripts
    public void handleModCommand(MessageReceivedEvent event, String commandName, String args) {
        scriptExecutor.submit(() -> {
            String key = "mod:" + commandName;
            List<String> handlers = prefixCommandHandlers.get(key);

            if (handlers == null || handlers.isEmpty()) {
                System.out.println("no script handler found for mod command: " + commandName);
                ScriptUtils utils = new ScriptUtils();
                EmbedBuilder embed = utils.createErrorEmbed("command not found",
                        "the command `" + commandName + "` was not found. use `$help` for available commands.");
                event.getMessage().replyEmbeds(embed.build()).queue();
                return;
            }

            System.out.println("executing mod command: " + commandName + " with args: " + args);

            for (String handlerName : handlers) {
                executePrefixCommandHandler(event, handlerName, commandName, args);
            }
        });
    }

    private void executePrefixCommandHandler(MessageReceivedEvent event, String handlerName, String commandName, String args) {
        try {
            Value handler = context.getBindings("js").getMember(handlerName);
            if (handler == null || !handler.canExecute()) {
                System.err.println("handler function missing or invalid: " + handlerName);
                ScriptUtils utils = new ScriptUtils();
                EmbedBuilder embed = utils.createErrorEmbed("script error",
                        "the command handler for `" + commandName + "` is not available.");
                event.getMessage().replyEmbeds(embed.build()).queue();
                return;
            }

            ScriptUtils utils = new ScriptUtils();
            try {
                // try calling with all utilities
                handler.execute(event, utils, dbManager, httpUtils, audioManager, scheduler, timeUtils, commandName, args);
                System.out.println("✓ successfully executed handler: " + handlerName);
            } catch (PolyglotException e) {
                // fallback to simpler signature
                if (e.getMessage().contains("Invalid number of arguments")) {
                    System.out.println("falling back to simple signature for: " + handlerName);
                    handler.execute(event, utils, commandName, args);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            System.err.println("error executing prefix command handler " + handlerName + ": " + e.getMessage());
            if (config.isDebugMode()) e.printStackTrace();

            ScriptUtils utils = new ScriptUtils();
            EmbedBuilder embed = utils.createErrorEmbed("execution error",
                    "an error occurred while executing the command: " + e.getMessage());
            event.getMessage().replyEmbeds(embed.build()).queue();
        }
    }

    public void handleLegacySlashCommand(MessageReceivedEvent event) {
        // provide migration message for old slash commands
        event.getMessage().reply("slash commands are deprecated. use prefix commands: `$help`").queue();
    }

    public boolean hasEventHandler(String eventType) {
        return eventHandlers.containsKey(eventType.toUpperCase(Locale.ROOT));
    }

    public void executeEventHandler(String eventType, GenericEvent event) {
        scriptExecutor.submit(() -> {
            List<String> handlers = eventHandlers.get(eventType.toUpperCase(Locale.ROOT));
            if (handlers == null) return;

            handlers.forEach(handlerName -> {
                try {
                    ScriptUtils utils = new ScriptUtils();
                    context.getBindings("js").getMember(handlerName)
                            .execute(event, utils, dbManager, httpUtils, audioManager, scheduler, timeUtils);
                } catch (Exception e) {
                    System.err.println("error in event handler " + handlerName + ": " + e.getMessage());
                    if (config.isDebugMode()) e.printStackTrace();
                }
            });
        });
    }

    public void executeScheduledTask(String scriptFileName, String handlerName, JDA jda) {
        scriptExecutor.submit(() -> {
            try {
                ScriptUtils utils = new ScriptUtils();
                context.getBindings("js").getMember(handlerName)
                        .execute(jda, utils, dbManager, httpUtils, audioManager, scheduler, timeUtils);
            } catch (Exception e) {
                System.err.println("error in scheduled task " + handlerName + ": " + e.getMessage());
                if (config.isDebugMode()) e.printStackTrace();
            }
        });
    }

    private void initializeContext() {
        if (context != null) {
            context.close();
        }

        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowIO(IOAccess.ALL)
                .allowAllAccess(config.isEnableJsConsoleAccess())
                .option("js.ecmascript-version", "2022")
                .build();
    }

    public void shutdown() {
        scriptExecutor.shutdown();
        try {
            if (!scriptExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scriptExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scriptExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (context != null) {
            context.close();
        }
    }
}