package xyz.plavpixel.mycelium.util;

import xyz.plavpixel.mycelium.script.ScriptManager;
import net.dv8tion.jda.api.JDA;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * handles scheduled task execution
 */
public class Scheduler {
    private final ScheduledExecutorService executorService;
    private final ScriptManager scriptManager;
    private final JDA jda;

    public Scheduler(ScriptManager scriptManager, JDA jda) {
        this.executorService = Executors.newScheduledThreadPool(5);
        this.scriptManager = scriptManager;
        this.jda = jda;
    }

    public void scheduleOnce(String scriptFileName, String handlerName, long delay, String timeUnit) {
        Runnable task = () -> scriptManager.executeScheduledTask(scriptFileName, handlerName, jda);
        executorService.schedule(task, delay, TimeUnit.valueOf(timeUnit.toUpperCase()));
        System.out.printf("scheduled task '%s' in '%s' to run once in %d %s%n",
                handlerName, scriptFileName, delay, timeUnit);
    }

    public void scheduleRepeating(String scriptFileName, String handlerName, long initialDelay, long period, String timeUnit) {
        Runnable task = () -> scriptManager.executeScheduledTask(scriptFileName, handlerName, jda);
        executorService.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.valueOf(timeUnit.toUpperCase()));
        System.out.printf("scheduled task '%s' in '%s' to run every %d %s%n",
                handlerName, scriptFileName, period, timeUnit);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}