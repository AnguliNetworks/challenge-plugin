package li.angu.challengeplugin.database

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.database.migrations.Migration001CreateInitialTables
import li.angu.challengeplugin.database.migrations.Migration002AddChallengeSettings
import li.angu.challengeplugin.database.migrations.Migration003AddPlayerData
import li.angu.challengeplugin.database.migrations.Migration004AddChallengeParticipants
import li.angu.challengeplugin.database.migrations.Migration005AddRandomizerMappings
import li.angu.challengeplugin.database.migrations.Migration006UpdateChallengesTable
import li.angu.challengeplugin.database.migrations.Migration007UpdateChallengeSettings
import li.angu.challengeplugin.database.migrations.Migration008AddPotionEffects
import li.angu.challengeplugin.database.migrations.Migration009AddArmorData
import java.sql.SQLException
import java.util.logging.Level

class MigrationManager(
    private val databaseDriver: DatabaseDriver,
    private val plugin: ChallengePluginPlugin
) {
    
    private val migrations = listOf(
        Migration001CreateInitialTables(),
        Migration002AddChallengeSettings(),
        Migration003AddPlayerData(),
        Migration004AddChallengeParticipants(),
        Migration005AddRandomizerMappings(),
        Migration006UpdateChallengesTable(),
        Migration007UpdateChallengeSettings(),
        Migration008AddPotionEffects(),
        Migration009AddArmorData()
    )
    
    /**
     * Run all pending migrations
     * Throws exception if any migration fails, aborting plugin startup
     */
    fun runMigrations() {
        try {
            // Test database integrity first
            try {
                val result = databaseDriver.executeQuery("PRAGMA integrity_check") { rs ->
                    if (rs.next()) rs.getString(1) else "failed"
                } ?: "failed"
                
                plugin.logger.info("Database integrity check: $result")
                if (result != "ok") {
                    plugin.logger.severe("Database integrity check failed: $result")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not run integrity check: ${e.message}")
            }
            
            // Create schema_migrations table if it doesn't exist
            createMigrationTable()
            
            // Get current schema version
            val currentVersion = getCurrentSchemaVersion()
            plugin.logger.info("Current schema version: $currentVersion")
            
            // Log all available migrations for debugging
            plugin.logger.info("Available migrations: ${migrations.map { "${it.version}: ${it.description}" }}")
            
            // Get pending migrations
            val pendingMigrations = migrations
                .filter { it.version > currentVersion }
                .sortedBy { it.version }
            
            plugin.logger.info("Pending migrations: ${pendingMigrations.map { "${it.version}: ${it.description}" }}")
            
            if (pendingMigrations.isEmpty()) {
                plugin.logger.info("No pending migrations. Current schema version: ${getCurrentSchemaVersion()}")
                return
            }
            
            plugin.logger.info("Found ${pendingMigrations.size} pending migrations to execute")
            
            // Run pending migrations - STOP on first failure
            for (migration in pendingMigrations) {
                plugin.logger.info("Running migration ${migration.version}: ${migration.description}")
                
                if (!migration.execute(databaseDriver)) {
                    val errorMessage = "Migration ${migration.version} (${migration.description}) failed! Plugin startup aborted."
                    plugin.logger.severe(errorMessage)
                    throw SQLException(errorMessage)
                }
                
                // Update schema version
                updateSchemaVersion(migration.version)
                plugin.logger.info("Migration ${migration.version} completed successfully")
            }
            
            plugin.logger.info("All migrations completed successfully. Current schema version: ${getCurrentSchemaVersion()}")
            
            // Force synchronization to disk so external tools can see the changes
            databaseDriver.sync()
            
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Database migration failed - plugin startup aborted", e)
            throw e
        }
    }
    
    private fun createMigrationTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                executed_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        
        databaseDriver.executeUpdate(sql)
    }
    
    private fun getCurrentSchemaVersion(): Int {
        return try {
            plugin.logger.info("Querying current schema version...")
            
            // First, let's check if the table exists and what's in it
            val count = databaseDriver.executeQuery("SELECT COUNT(*) as count FROM schema_migrations") { rs ->
                if (rs.next()) rs.getInt("count") else 0
            } ?: 0
            plugin.logger.info("Found $count entries in schema_migrations table")
            
            // Check if the table exists at all  
            val tableExists = databaseDriver.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='schema_migrations'") { rs ->
                rs.next()
            } ?: false
            plugin.logger.info("schema_migrations table exists: $tableExists")
            
            // List all tables to see what exists
            val tables = databaseDriver.executeQuery("SELECT name FROM sqlite_master WHERE type='table'") { rs ->
                val tableList = mutableListOf<String>()
                while (rs.next()) {
                    tableList.add(rs.getString("name"))
                }
                tableList
            } ?: emptyList()
            plugin.logger.info("All tables in database: $tables")
            
            // If database file exists with size > 0 but no tables found, it might be corrupted
            if (tables.isEmpty() && databaseDriver.getDatabaseFile().exists() && databaseDriver.getDatabaseFile().length() > 0) {
                plugin.logger.warning("Database file exists with ${databaseDriver.getDatabaseFile().length()} bytes but no tables found - possible corruption")
                throw SQLException("Database appears corrupted - contains data but no readable tables. Please delete the database file manually: ${databaseDriver.getDatabaseFile().absolutePath}")
            }
            
            // Now let's see all versions in the table
            val versions = databaseDriver.executeQuery("SELECT version, description FROM schema_migrations ORDER BY version") { rs ->
                val versionList = mutableListOf<String>()
                while (rs.next()) {
                    versionList.add("${rs.getInt("version")}: ${rs.getString("description")}")
                }
                versionList
            } ?: emptyList()
            plugin.logger.info("Existing migrations in database: $versions")
            
            // Now get the max version
            val maxVersion = databaseDriver.executeQuery("SELECT MAX(version) as version FROM schema_migrations") { rs ->
                if (rs.next()) {
                    val version = rs.getInt("version")
                    plugin.logger.info("MAX version query returned: $version")
                    version
                } else {
                    plugin.logger.info("No rows found in MAX version query, returning version 0")
                    0
                }
            } ?: 0
            
            maxVersion
        } catch (e: SQLException) {
            plugin.logger.warning("Failed to query schema version: ${e.message}")
            e.printStackTrace()
            0 // If table doesn't exist or query fails, assume version 0
        }
    }
    
    private fun updateSchemaVersion(version: Int) {
        val migration = migrations.find { it.version == version }
        val description = migration?.description ?: "Unknown migration"
        
        plugin.logger.info("Updating schema version to $version: $description")
        
        val rowsAffected = databaseDriver.executeUpdate(
            "INSERT INTO schema_migrations (version, description) VALUES (?, ?)",
            version, description
        )
        
        if (rowsAffected > 0) {
            plugin.logger.info("Successfully recorded migration $version in schema_migrations table")
        } else {
            plugin.logger.warning("Failed to record migration $version in schema_migrations table (0 rows affected)")
        }
        
        // Verify the insert worked by immediately querying back
        val count = databaseDriver.executeQuery("SELECT COUNT(*) as count FROM schema_migrations WHERE version = ?", version) { rs ->
            if (rs.next()) rs.getInt("count") else 0
        } ?: 0
        plugin.logger.info("Verification: Found $count entries for migration $version in schema_migrations table")
    }
}