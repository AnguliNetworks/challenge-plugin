package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 003: Add player data storage
 */
class Migration003AddPlayerData : Migration() {
    override val version = 3
    override val description = "Add player data storage for inventory and location"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS player_challenge_data (
                player_uuid TEXT NOT NULL,
                challenge_id TEXT NOT NULL,
                inventory_data TEXT NULL,
                ender_chest_data TEXT NULL,
                location_world TEXT NULL,
                location_x REAL NULL,
                location_y REAL NULL,
                location_z REAL NULL,
                location_yaw REAL NULL,
                location_pitch REAL NULL,
                health REAL NULL,
                food_level INTEGER NULL,
                experience_points INTEGER NULL,
                experience_level INTEGER NULL,
                game_mode TEXT NULL,
                saved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, challenge_id),
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_player_data_challenge ON player_challenge_data(challenge_id)
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_player_data_saved_at ON player_challenge_data(saved_at)
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}