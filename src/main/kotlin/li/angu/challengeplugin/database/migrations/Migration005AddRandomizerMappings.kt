package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 005: Add randomizer mappings table for block randomizer feature
 */
class Migration005AddRandomizerMappings : Migration() {
    override val version = 5
    override val description = "Add randomizer mappings table for block randomizer feature"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS randomizer_mappings (
                challenge_id TEXT NOT NULL,
                source_material TEXT NOT NULL,
                target_material TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (challenge_id, source_material),
                FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            """
            CREATE INDEX IF NOT EXISTS idx_randomizer_mappings_challenge ON randomizer_mappings(challenge_id)
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}