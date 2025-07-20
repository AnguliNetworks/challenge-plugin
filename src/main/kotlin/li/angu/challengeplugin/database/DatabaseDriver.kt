package li.angu.challengeplugin.database

import li.angu.challengeplugin.ChallengePluginPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.logging.Level

class DatabaseDriver(private val plugin: ChallengePluginPlugin) {

    private val databaseFile = File(plugin.dataFolder, "challenges.db")
    private var connection: Connection? = null
    private val migrationManager = MigrationManager(this, plugin)

    /**
     * Initialize the database connection and run migrations
     * Returns false if initialization fails, which should abort plugin startup
     */
    fun initialize(): Boolean {
        try {
            // Ensure data folder exists
            plugin.dataFolder.mkdirs()

            plugin.logger.info("Plugin data folder: ${plugin.dataFolder.absolutePath}")
            plugin.logger.info("Database file path: ${databaseFile.absolutePath}")
            plugin.logger.info("Database file exists: ${databaseFile.exists()}")
            if (databaseFile.exists()) {
                plugin.logger.info("Database file size: ${databaseFile.length()} bytes")
            }

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC")

            // Create connection
            val connectionString = "jdbc:sqlite:${databaseFile.absolutePath}"
            plugin.logger.info("Connecting to database with: $connectionString")
            connection = DriverManager.getConnection(connectionString)
            
            plugin.logger.info("Database connection established: ${connection != null}")
            plugin.logger.info("Database connection valid: ${connection?.isValid(5)}")

            // Enable foreign keys
            connection?.createStatement()?.use { stmt ->
                stmt.execute("PRAGMA foreign_keys = ON")
                plugin.logger.info("Foreign keys enabled")
                
                // Test if we can read basic SQLite info
                val result = stmt.executeQuery("PRAGMA database_list")
                while (result.next()) {
                    plugin.logger.info("Database: name=${result.getString("name")}, file=${result.getString("file")}")
                }
                result.close()
            }

            // Run migrations - this will throw an exception if any migration fails
            migrationManager.runMigrations()

            plugin.logger.info("Database initialized successfully at: ${databaseFile.absolutePath}")
            return true

        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize database - plugin startup aborted", e)
            // Close connection if it was opened
            connection?.close()
            connection = null
            return false
        }
    }

    /**
     * Get the database file
     */
    fun getDatabaseFile(): File = databaseFile

    /**
     * Get the current database connection
     */
    fun getConnection(): Connection? {
        return connection
    }

    /**
     * Execute a query and process results immediately (SAFE - resources are properly managed)
     */
    fun <T> executeQuery(sql: String, vararg params: Any, processor: (ResultSet) -> T): T? {
        return try {
            val conn = getConnection() ?: return null

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

                statement.executeQuery().use { resultSet ->
                    processor(resultSet)
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to execute query: $sql", e)
            null
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
     * Execute a batch of parameterized statements in a transaction
     */
    fun executeTransaction(operations: List<Pair<String, Array<Any?>>>): Boolean {
        return try {
            val conn = getConnection() ?: return false

            plugin.logger.info("Starting database transaction with ${operations.size} operations")
            conn.autoCommit = false

            try {
                operations.forEachIndexed { index, (sql, params) ->
                    plugin.logger.info("Executing operation $index: $sql")
                    plugin.logger.info("Parameters: ${params.joinToString(", ")}")
                    
                    conn.prepareStatement(sql).use { statement ->
                        params.forEachIndexed { paramIndex, param ->
                            when (param) {
                                null -> statement.setNull(paramIndex + 1, java.sql.Types.NULL)
                                is String -> statement.setString(paramIndex + 1, param)
                                is Int -> statement.setInt(paramIndex + 1, param)
                                is Long -> statement.setLong(paramIndex + 1, param)
                                is Boolean -> statement.setBoolean(paramIndex + 1, param)
                                is Double -> statement.setDouble(paramIndex + 1, param)
                                else -> statement.setObject(paramIndex + 1, param)
                            }
                        }
                        val rowsAffected = statement.executeUpdate()
                        plugin.logger.info("Operation $index affected $rowsAffected rows")
                    }
                }
                
                plugin.logger.info("Committing transaction")
                conn.commit()
                plugin.logger.info("Transaction committed successfully")
                true
            } catch (e: SQLException) {
                plugin.logger.severe("Transaction failed, rolling back: ${e.message}")
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to execute parameterized batch", e)
            false
        }
    }

    /**
     * Check if a table exists
     */
    fun tableExists(tableName: String): Boolean {
        return try {
            executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                tableName
            ) { rs ->
                rs.next()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Force synchronization to disk
     */
    fun sync() {
        try {
            plugin.logger.info("Database sync requested for file: ${databaseFile.absolutePath}")
            plugin.logger.info("Database file exists: ${databaseFile.exists()}, size: ${if (databaseFile.exists()) databaseFile.length() else 0} bytes")
            
            // Execute PRAGMA synchronous to ensure data is written
            getConnection()?.createStatement()?.use { stmt ->
                stmt.execute("PRAGMA synchronous = FULL")
                stmt.execute("PRAGMA wal_checkpoint(FULL)")
                plugin.logger.info("Executed PRAGMA synchronous and WAL checkpoint")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error during database sync: ${e.message}")
        }
    }

    /**
     * Close the database connection
     */
    fun close() {
        try {
            sync() // Ensure everything is written to disk before closing
            connection?.close()
            plugin.logger.info("Database connection closed")
        } catch (e: SQLException) {
            plugin.logger.log(Level.WARNING, "Error closing database connection", e)
        }
    }
}
