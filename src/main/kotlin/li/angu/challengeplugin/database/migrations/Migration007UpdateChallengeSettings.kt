package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 007: Update challenge settings with border size field
 */
class Migration007UpdateChallengeSettings : Migration() {
    override val version = 7
    override val description = "Update challenge settings with border size field"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            ALTER TABLE challenge_settings ADD COLUMN border_size REAL DEFAULT 0.0
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}