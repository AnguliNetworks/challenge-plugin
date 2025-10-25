package li.angu.challengeplugin.database.migrations

import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.database.Migration

/**
 * Migration 011: Add difficulty setting to challenge_settings table
 */
class Migration011AddDifficulty : Migration() {
    override val version = 11
    override val description = "Add difficulty field to challenge settings"

    override fun execute(driver: DatabaseDriver): Boolean {
        val statements = listOf(
            """
            ALTER TABLE challenge_settings ADD COLUMN difficulty TEXT DEFAULT 'HARDCORE'
            """.trimIndent()
        )

        return driver.executeBatch(statements)
    }
}
