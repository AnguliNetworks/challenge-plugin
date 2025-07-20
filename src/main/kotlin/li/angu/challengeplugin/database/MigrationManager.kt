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
            
            // Get pending migrations
            val pendingMigrations = migrations
                .filter { it.version > currentVersion }
                .sortedBy { it.version }
            
            if (pendingMigrations.isEmpty()) {
                plugin.logger.info("No pending migrations")
                return
            }
            
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
            
            plugin.logger.info("All migrations completed successfully")
            
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
            // Check if database file exists with size > 0 but no tables found (corruption check)
            val tables = databaseDriver.executeQuery("SELECT name FROM sqlite_master WHERE type='table'") { rs ->
                val tableList = mutableListOf<String>()
                while (rs.next()) {
                    tableList.add(rs.getString("name"))
                }
                tableList
            } ?: emptyList()
            
            // If database file exists with size > 0 but no tables found, it might be corrupted
            if (tables.isEmpty() && databaseDriver.getDatabaseFile().exists() && databaseDriver.getDatabaseFile().length() > 0) {
                plugin.logger.warning("Database file exists with ${databaseDriver.getDatabaseFile().length()} bytes but no tables found - possible corruption")
                throw SQLException("Database appears corrupted - contains data but no readable tables. Please delete the database file manually: ${databaseDriver.getDatabaseFile().absolutePath}")
            }
            
            // Get the max version
            val maxVersion = databaseDriver.executeQuery("SELECT MAX(version) as version FROM schema_migrations") { rs ->
                if (rs.next()) {
                    rs.getInt("version")
                } else {
                    0
                }
            } ?: 0
            
            maxVersion
        } catch (e: SQLException) {
            plugin.logger.warning("Failed to query schema version: ${e.message}")
            0 // If table doesn't exist or query fails, assume version 0
        }
    }
    
    private fun updateSchemaVersion(version: Int) {
        val migration = migrations.find { it.version == version }
        val description = migration?.description ?: "Unknown migration"
        
        val rowsAffected = databaseDriver.executeUpdate(
            "INSERT INTO schema_migrations (version, description) VALUES (?, ?)",
            version, description
        )
        
        if (rowsAffected <= 0) {
            plugin.logger.warning("Failed to record migration $version in schema_migrations table (0 rows affected)")
        }
    }
}