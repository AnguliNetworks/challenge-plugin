package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 010: Add saturation field to player challenge data
 */
class Migration010AddSaturation : Migration() {
    override val version = 10
    override val description = "Add saturation field to player challenge data"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            ALTER TABLE player_challenge_data 
            ADD COLUMN saturation REAL NULL DEFAULT 20.0
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}