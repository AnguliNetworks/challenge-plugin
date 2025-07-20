package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 004: Add challenge participants table for many-to-many relationship
 */
class Migration004AddChallengeParticipants : Migration() {
    override val version = 4
    override val description = "Add challenge participants table for many-to-many relationship"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS challenge_participants (
                challenge_id TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (challenge_id, player_uuid),
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_challenge_participants_challenge ON challenge_participants(challenge_id)
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_challenge_participants_player ON challenge_participants(player_uuid)
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}