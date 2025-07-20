package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 006: Update challenges table with additional fields from YAML
 */
class Migration006UpdateChallengesTable : Migration() {
    override val version = 6
    override val description = "Update challenges table with additional fields from YAML"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            ALTER TABLE challenges ADD COLUMN started_at DATETIME NULL
            """.trimIndent(),
            
            """
            ALTER TABLE challenges ADD COLUMN paused_at DATETIME NULL
            """.trimIndent(),
            
            """
            ALTER TABLE challenges ADD COLUMN last_empty_timestamp DATETIME NULL
            """.trimIndent(),
            
            """
            ALTER TABLE challenges ADD COLUMN total_paused_duration INTEGER DEFAULT 0
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}