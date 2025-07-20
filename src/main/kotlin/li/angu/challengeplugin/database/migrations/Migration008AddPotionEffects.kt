package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 008: Add potion effects table for player data
 */
class Migration008AddPotionEffects : Migration() {
    override val version = 8
    override val description = "Add potion effects table for player data"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS player_potion_effects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                challenge_id TEXT NOT NULL,
                effect_type TEXT NOT NULL,
                duration INTEGER NOT NULL,
                amplifier INTEGER NOT NULL,
                ambient BOOLEAN NOT NULL DEFAULT FALSE,
                particles BOOLEAN NOT NULL DEFAULT TRUE,
                icon BOOLEAN NOT NULL DEFAULT TRUE,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (player_uuid, challenge_id) REFERENCES player_challenge_data(player_uuid, challenge_id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_player_potion_effects_player_challenge ON player_potion_effects(player_uuid, challenge_id)
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}