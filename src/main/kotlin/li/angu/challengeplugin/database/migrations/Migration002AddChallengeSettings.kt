package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 002: Add challenge settings table
 */
class Migration002AddChallengeSettings : Migration() {
    override val version = 2
    override val description = "Add challenge settings storage"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS challenge_settings (
                challenge_id TEXT PRIMARY KEY,
                natural_regeneration BOOLEAN DEFAULT TRUE,
                sync_hearts BOOLEAN DEFAULT FALSE,
                block_randomizer BOOLEAN DEFAULT FALSE,
                level_world_border BOOLEAN DEFAULT FALSE,
                starter_kit TEXT DEFAULT 'NONE',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            """
            CREATE TRIGGER IF NOT EXISTS update_challenge_settings_timestamp 
            AFTER UPDATE ON challenge_settings
            BEGIN
                UPDATE challenge_settings SET updated_at = CURRENT_TIMESTAMP WHERE challenge_id = NEW.challenge_id;
            END
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}