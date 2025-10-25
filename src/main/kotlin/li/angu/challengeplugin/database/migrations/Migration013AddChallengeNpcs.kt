package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 013: Add challenge NPCs table
 */
class Migration013AddChallengeNpcs : Migration() {
    override val version = 13
    override val description = "Add challenge NPCs table for join NPCs"

    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS challenge_npcs (
                id TEXT PRIMARY KEY,
                challenge_id TEXT NOT NULL,
                world_name TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                yaw REAL NOT NULL,
                pitch REAL NOT NULL,
                entity_uuid TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),

            """
            CREATE INDEX IF NOT EXISTS idx_challenge_npcs_challenge ON challenge_npcs(challenge_id)
            """.trimIndent(),

            """
            CREATE INDEX IF NOT EXISTS idx_challenge_npcs_entity ON challenge_npcs(entity_uuid)
            """.trimIndent()
        )

        return driver.executeBatch(statements)
    }
}
