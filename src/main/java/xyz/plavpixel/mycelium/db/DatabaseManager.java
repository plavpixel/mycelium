package xyz.plavpixel.mycelium.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.plavpixel.mycelium.config.BotConfig;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * manages database operations with connection pooling and async support
 */
public class DatabaseManager {
    private final String dbUrl;
    private final BotConfig config;
    private final ExecutorService executor;
    private final ObjectMapper mapper;

    public DatabaseManager() {
        this.config = BotConfig.getInstance();
        this.dbUrl = "jdbc:sqlite:" + config.getDatabasePath();
        this.executor = Executors.newFixedThreadPool(4);
        this.mapper = new ObjectMapper();

        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            // mod logs table
            String modLogsSql = "CREATE TABLE IF NOT EXISTS mod_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guild_id TEXT NOT NULL," +
                    "moderator_id TEXT NOT NULL," +
                    "target_id TEXT NOT NULL," +
                    "action TEXT NOT NULL," +
                    "reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

            // command permissions table
            String permissionsSql = "CREATE TABLE IF NOT EXISTS command_permissions (" +
                    "guild_id TEXT NOT NULL," +
                    "command_name TEXT NOT NULL," +
                    "permission_type TEXT NOT NULL," +
                    "target_id TEXT NOT NULL," +
                    "PRIMARY KEY (guild_id, command_name)" +
                    ");";

            // guild settings table
            String guildSettingsSql = "CREATE TABLE IF NOT EXISTS guild_settings (" +
                    "guild_id TEXT PRIMARY KEY," +
                    "user_prefix TEXT," +
                    "mod_prefix TEXT," +
                    "log_channel TEXT," +
                    "welcome_channel TEXT," +
                    "auto_mod_enabled BOOLEAN DEFAULT FALSE," +
                    "max_warnings INTEGER DEFAULT 3" +
                    ");";

            // user warnings table
            String warningsSql = "CREATE TABLE IF NOT EXISTS user_warnings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guild_id TEXT NOT NULL," +
                    "user_id TEXT NOT NULL," +
                    "moderator_id TEXT NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(modLogsSql);
                stmt.execute(permissionsSql);
                stmt.execute(guildSettingsSql);
                stmt.execute(warningsSql);
                System.out.println("initialized database tables");
            }
        } catch (SQLException e) {
            System.err.println("error initializing database: " + e.getMessage());
        }
    }

    /**
     * execute an update query asynchronously
     */
    public CompletableFuture<Void> executeAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            execute(sql, params);
        }, executor);
    }

    /**
     * execute an update query synchronously
     */
    public void execute(String sql, Object... params) {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("db execute error: " + e.getMessage());
            if (config.isDebugMode()) e.printStackTrace();
        }
    }

    /**
     * execute a query and return results as json string
     */
    public String query(String sql, Object... params) {
        ArrayNode results = mapper.createArrayNode();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();

            while (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                for (int i = 1; i <= columns; i++) {
                    String columnName = md.getColumnName(i);
                    Object value = rs.getObject(i);
                    if (value != null) {
                        row.put(columnName, value.toString());
                    } else {
                        row.putNull(columnName);
                    }
                }
                results.add(row);
            }
        } catch (SQLException e) {
            System.err.println("db query error: " + e.getMessage());
            if (config.isDebugMode()) e.printStackTrace();
            return "[]";
        }
        return results.toString();
    }

    /**
     * query asynchronously
     */
    public CompletableFuture<String> queryAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> query(sql, params), executor);
    }

    /**
     * get a single value from query
     */
    public String querySingle(String sql, Object... params) {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            System.err.println("db query single error: " + e.getMessage());
            if (config.isDebugMode()) e.printStackTrace();
        }
        return null;
    }

    /**
     * check if a record exists
     */
    public boolean exists(String sql, Object... params) {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("db exists check error: " + e.getMessage());
            if (config.isDebugMode()) e.printStackTrace();
        }
        return false;
    }

    public void shutdown() {
        executor.shutdown();
    }
}