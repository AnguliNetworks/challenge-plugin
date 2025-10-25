package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 012: Add player bed spawn locations table
 */
class Migration012AddBedSpawns : Migration() {
    override val version = 12
    override val description = "Add player bed spawn locations per challenge"

    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS player_bed_spawns (
                player_uuid TEXT NOT NULL,
                challenge_id TEXT NOT NULL,
                world_name TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                yaw REAL NOT NULL,
                pitch REAL NOT NULL,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, challenge_id),
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),

            """
            CREATE INDEX IF NOT EXISTS idx_bed_spawns_challenge ON player_bed_spawns(challenge_id)
            """.trimIndent()
        )

        return driver.executeBatch(statements)
    }
}
