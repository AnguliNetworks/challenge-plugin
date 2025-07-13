package li.angu.challengeplugin.database

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.database.migrations.Migration001CreateInitialTables
import li.angu.challengeplugin.database.migrations.Migration002AddChallengeSettings
import li.angu.challengeplugin.database.migrations.Migration003AddPlayerData
import java.sql.SQLException
import java.util.logging.Level

class MigrationManager(
    private val databaseDriver: DatabaseDriver,
    private val plugin: ChallengePluginPlugin
) {
    
    private val migrations = listOf(
        Migration001CreateInitialTables(),
        Migration002AddChallengeSettings(),
        Migration003AddPlayerData()
    )
    
    /**
     * Run all pending migrations
     */
    fun runMigrations() {
        try {
            // Create schema_migrations table if it doesn't exist
            createMigrationTable()
            
            // Get current schema version
            val currentVersion = getCurrentSchemaVersion()
            
            // Run pending migrations
            migrations
                .filter { it.version > currentVersion }
                .sortedBy { it.version }
                .forEach { migration ->
                    plugin.logger.info("Running migration ${migration.version}: ${migration.description}")
                    
                    if (migration.execute(databaseDriver)) {
                        // Update schema version
                        updateSchemaVersion(migration.version)
                        plugin.logger.info("Migration ${migration.version} completed successfully")
                    } else {
                        throw SQLException("Migration ${migration.version} failed")
                    }
                }
            
            plugin.logger.info("All migrations completed. Current schema version: ${getCurrentSchemaVersion()}")
            
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Migration failed", e)
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
            val result = databaseDriver.executeQuery("SELECT MAX(version) as version FROM schema_migrations")
            result.resultSet?.use { rs ->
                if (rs.next()) {
                    rs.getInt("version")
                } else {
                    0
                }
            } ?: 0
        } catch (e: SQLException) {
            0 // If table doesn't exist or query fails, assume version 0
        }
    }
    
    private fun updateSchemaVersion(version: Int) {
        val migration = migrations.find { it.version == version }
        val description = migration?.description ?: "Unknown migration"
        
        databaseDriver.executeUpdate(
            "INSERT INTO schema_migrations (version, description) VALUES (?, ?)",
            version, description
        )
    }
}