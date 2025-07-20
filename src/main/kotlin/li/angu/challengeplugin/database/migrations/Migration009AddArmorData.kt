package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 009: Add armor data storage to player challenge data
 */
class Migration009AddArmorData : Migration() {
    override val version = 9
    override val description = "Add armor data storage to player challenge data"
    
    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            ALTER TABLE player_challenge_data 
            ADD COLUMN armor_data TEXT NULL
            """.trimIndent()
        )
        
        return driver.executeBatch(statements)
    }
}