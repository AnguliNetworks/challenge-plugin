package li.angu.challengeplugin.database

/**
 * Base class for database migrations
 */
abstract class Migration {
    abstract val version: Int
    abstract val description: String
    
    /**
     * Execute the migration
     * @param driver The database driver to use
     * @return true if successful, false otherwise
     */
    abstract fun execute(driver: DatabaseDriver): Boolean
}