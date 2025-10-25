package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Location
import org.bukkit.Material
import java.util.UUID

/**
 * Manages player bed spawn locations for challenges.
 * Tracks where players have set their spawn point by sleeping in beds.
 */
class BedSpawnManager(private val plugin: ChallengePluginPlugin) {

    /**
     * Saves or updates the bed spawn location for a player in a challenge.
     * Uses INSERT OR REPLACE to update if already exists.
     */
    fun saveBedSpawn(playerUuid: UUID, challengeId: UUID, location: Location) {
        val query = """
            INSERT OR REPLACE INTO player_bed_spawns
            (player_uuid, challenge_id, world_name, x, y, z, yaw, pitch, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """.trimIndent()

        plugin.databaseDriver.executeUpdate(
            query,
            playerUuid.toString(),
            challengeId.toString(),
            location.world?.name ?: "",
            location.x,
            location.y,
            location.z,
            location.yaw.toDouble(),
            location.pitch.toDouble()
        )

        plugin.logger.fine("Saved bed spawn for player $playerUuid in challenge $challengeId at ${location.world?.name} (${location.blockX}, ${location.blockY}, ${location.blockZ})")
    }

    /**
     * Gets the saved bed spawn location for a player in a challenge.
     * Returns null if no bed spawn is saved.
     */
    fun getBedSpawn(playerUuid: UUID, challengeId: UUID): Location? {
        val query = """
            SELECT world_name, x, y, z, yaw, pitch
            FROM player_bed_spawns
            WHERE player_uuid = ? AND challenge_id = ?
        """.trimIndent()

        return plugin.databaseDriver.executeQuery(query, playerUuid.toString(), challengeId.toString()) { rs ->
            if (rs.next()) {
                val worldName = rs.getString("world_name")
                val world = plugin.server.getWorld(worldName)

                if (world == null) {
                    plugin.logger.warning("World $worldName not found for bed spawn")
                    return@executeQuery null
                }

                Location(
                    world,
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getDouble("yaw").toFloat(),
                    rs.getDouble("pitch").toFloat()
                )
            } else {
                null
            }
        }
    }

    /**
     * Deletes the saved bed spawn location for a player in a challenge.
     */
    fun deleteBedSpawn(playerUuid: UUID, challengeId: UUID) {
        val query = """
            DELETE FROM player_bed_spawns
            WHERE player_uuid = ? AND challenge_id = ?
        """.trimIndent()

        plugin.databaseDriver.executeUpdate(query, playerUuid.toString(), challengeId.toString())
        plugin.logger.fine("Deleted bed spawn for player $playerUuid in challenge $challengeId")
    }

    /**
     * Checks if a bed block exists at the given location.
     * Returns true if a bed is present, false otherwise.
     */
    fun isBedValid(location: Location): Boolean {
        val world = location.world ?: return false
        val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)
        return block.type.name.contains("BED")
    }
}
