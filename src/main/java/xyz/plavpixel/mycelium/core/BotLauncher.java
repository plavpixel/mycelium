package xyz.plavpixel.mycelium.core;

import xyz.plavpixel.mycelium.config.BotConfig;
import xyz.plavpixel.mycelium.audio.AudioManager;
import xyz.plavpixel.mycelium.commands.CommandManager;
import xyz.plavpixel.mycelium.db.DatabaseManager;
import xyz.plavpixel.mycelium.events.EventManager;
import xyz.plavpixel.mycelium.script.ScriptManager;
import xyz.plavpixel.mycelium.util.Scheduler;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * main bot launcher class - handles initialization and startup
 */
public class BotLauncher {
    private JDA jda;
    private BotConfig config;
    private DatabaseManager dbManager;
    private AudioManager audioManager;
    private CommandManager commandManager;
    private ScriptManager scriptManager;

    public static void main(String[] args) throws InterruptedException { new BotLauncher().start(); }

    public void start() throws InterruptedException {
        printBanner();

        // load configuration and core services
        config = BotConfig.getInstance();
        createDirectories();

        Dotenv dotenv = Dotenv.load();
        dbManager = new DatabaseManager();
        audioManager = new AudioManager();

        // initialize script manager and load scripts
        scriptManager = new ScriptManager(dbManager, audioManager);
        scriptManager.loadScripts();

        // check token
        String token = dotenv.get("DISCORD_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            System.err.println("error: discord_token is missing from the .env file.");
            System.exit(1);
        }

        // build jda instance
        jda = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.ROLE_TAGS, CacheFlag.VOICE_STATE)
                .setActivity(config.getActivity())
                .addEventListeners(
                        new EventManager(scriptManager),
                        new CommandManager(dbManager, audioManager, scriptManager)
                )
                .build();

        jda.awaitReady();

        // initialize audio manager with jda
        audioManager.init(jda);

        // finalize setup
        Scheduler scheduler = new Scheduler(scriptManager, jda);
        scriptManager.setScheduler(scheduler);

        System.out.println("bot started successfully!");
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(config.getScriptsDirectory()));
            Files.createDirectories(Paths.get(config.getLogsDirectory()));
            Files.createDirectories(Paths.get(config.getDatabasePath()).getParent());
        } catch (IOException e) {
            System.err.println("warning: could not create required directories: " + e.getMessage());
        }
    }

    private void printBanner() {
        String asciiBanner = """

                                   _ _
                                  | (_)
          _ __ ___  _   _  ___ ___| |_ _   _ _ __ ___
         | '_ ` _ \\| | | |/ __/ _ \\ | | | | | '_ ` _ \\
         | | | | | | |_| | (_|  __/ | | |_| | | | | | |
         |_| |_| |_|\\__, |\\___\\___|_|_|\\__,_|_| |_| |_|
                     __/ |
                    |___/
        """;
        System.out.println(asciiBanner);
        System.out.println("mycelium v0.0.1 starting up...");
        System.out.println("-----------------------------------");
    }

    public JDA getJda() { return jda; }
    public BotConfig getConfig() { return config; }
    public DatabaseManager getDbManager() { return dbManager; }
    public AudioManager getAudioManager() { return audioManager; }
    public ScriptManager getScriptManager() { return scriptManager; }
}