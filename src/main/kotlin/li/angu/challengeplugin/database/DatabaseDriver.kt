package li.angu.challengeplugin.database

import li.angu.challengeplugin.ChallengePluginPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Level

class DatabaseDriver(private val plugin: ChallengePluginPlugin) {
    
    private val databaseFile = File(plugin.dataFolder, "challenges.db")
    private var connection: Connection? = null
    private val migrationManager = MigrationManager(this, plugin)
    
    /**
     * Initialize the database connection and run migrations
     */
    fun initialize(): Boolean {
        try {
            // Ensure data folder exists
            plugin.dataFolder.mkdirs()
            
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC")
            
            // Create connection
            connection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
            
            // Enable foreign keys
            connection?.createStatement()?.execute("PRAGMA foreign_keys = ON")
            
            // Run migrations
            migrationManager.runMigrations()
            
            plugin.logger.info("Database initialized successfully at: ${databaseFile.absolutePath}")
            return true
            
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize database", e)
            return false
        }
    }
    
    /**
     * Get the current database connection
     */
    fun getConnection(): Connection? {
        try {
            // Check if connection is valid
            if (connection?.isValid(5) == true) {
                return connection
            }
            
            // Reconnect if connection is invalid
            plugin.logger.warning("Database connection invalid, attempting to reconnect...")
            connection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
            connection?.createStatement()?.execute("PRAGMA foreign_keys = ON")
            
            return connection
            
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to get database connection", e)
            return null
        }
    }
    
    /**
     * Execute a query and return the result
     */
    fun executeQuery(sql: String, vararg params: Any): QueryResult {
        return try {
            val conn = getConnection() ?: return QueryResult.error("No database connection")
            
            conn.prepareStatement(sql).use { statement ->
                // Set parameters
                params.forEachIndexed { index, param ->
                    when (param) {
                        is String -> statement.setString(index + 1, param)
                        is Int -> statement.setInt(index + 1, param)
                        is Long -> statement.setLong(index + 1, param)
                        is Boolean -> statement.setBoolean(index + 1, param)
                        is Double -> statement.setDouble(index + 1, param)
                        else -> statement.setObject(index + 1, param)
                    }
                }
                
                val resultSet = statement.executeQuery()
                QueryResult.success(resultSet)
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to execute query: $sql", e)
            QueryResult.error(e.message ?: "Unknown SQL error")
        }
    }
    
    /**
     * Execute an update (INSERT, UPDATE, DELETE) and return affected rows
     */
    fun executeUpdate(sql: String, vararg params: Any): Int {
        return try {
            val conn = getConnection() ?: return -1
            
            conn.prepareStatement(sql).use { statement ->
                // Set parameters
                params.forEachIndexed { index, param ->
                    when (param) {
                        is String -> statement.setString(index + 1, param)
                        is Int -> statement.setInt(index + 1, param)
                        is Long -> statement.setLong(index + 1, param)
                        is Boolean -> statement.setBoolean(index + 1, param)
                        is Double -> statement.setDouble(index + 1, param)
                        else -> statement.setObject(index + 1, param)
                    }
                }
                
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to execute update: $sql", e)
            -1
        }
    }
    
    /**
     * Execute a batch of statements in a transaction
     */
    fun executeBatch(statements: List<String>): Boolean {
        return try {
            val conn = getConnection() ?: return false
            
            conn.autoCommit = false
            
            try {
                statements.forEach { sql ->
                    conn.createStatement().execute(sql)
                }
                conn.commit()
                true
            } catch (e: SQLException) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to execute batch", e)
            false
        }
    }
    
    /**
     * Check if a table exists
     */
    fun tableExists(tableName: String): Boolean {
        return try {
            val result = executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                tableName
            )
            
            result.resultSet?.use { rs ->
                rs.next()
            } ?: false
            
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Close the database connection
     */
    fun close() {
        try {
            connection?.close()
            plugin.logger.info("Database connection closed")
        } catch (e: SQLException) {
            plugin.logger.log(Level.WARNING, "Error closing database connection", e)
        }
    }
}