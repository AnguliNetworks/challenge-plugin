package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 001: Create initial tables for challenges and basic data
 */
class Migration001CreateInitialTables : Migration() {
    override val version = 1
    override val description = "Create initial tables for challenges"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS challenges (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                world_name TEXT,
                creator_uuid TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                completed_at DATETIME NULL,
                duration_seconds INTEGER DEFAULT 0
            )
            """.trimIndent(),
            
            """
            CREATE TABLE IF NOT EXISTS challenge_participants (
                challenge_id TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                left_at DATETIME NULL,
                is_active BOOLEAN DEFAULT TRUE,
                PRIMARY KEY (challenge_id, player_uuid),
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_challenges_status ON challenges(status)
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_challenges_creator ON challenges(creator_uuid)
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_participants_player ON challenge_participants(player_uuid)
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}