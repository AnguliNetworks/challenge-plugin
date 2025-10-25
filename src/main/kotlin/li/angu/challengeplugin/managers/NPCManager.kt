package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.EntityType
import org.bukkit.entity.IronGolem
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import kotlin.math.floor

/**
 * Manages challenge NPCs (Iron Golems) that players can interact with to join challenges.
 */
class NPCManager(private val plugin: ChallengePluginPlugin) {

    private val npcKey = NamespacedKey(plugin, "challenge_npc")
    private val npcIdKey = NamespacedKey(plugin, "challenge_npc_id")

    /**
     * Data class representing an NPC
     */
    data class ChallengeNPC(
        val id: UUID,
        val challengeId: UUID,
        val location: Location,
        var entityUuid: UUID?
    )

    /**
     * Create a new NPC at the specified location for the given challenge
     */
    fun createNPC(challengeId: UUID, location: Location): UUID? {
        val challenge = plugin.challengeManager.getChallenge(challengeId)
        if (challenge == null) {
            plugin.logger.warning("Cannot create NPC: Challenge not found with ID $challengeId")
            return null
        }

        val world = location.world
        if (world == null) {
            plugin.logger.warning("Cannot create NPC: World is null")
            return null
        }

        // Center the location on the block
        val centeredLocation = Location(
            world,
            floor(location.x) + 0.5,
            location.y,
            floor(location.z) + 0.5,
            location.yaw,
            location.pitch
        )

        // Generate NPC ID
        val npcId = UUID.randomUUID()

        // Spawn the Iron Golem
        val entity = world.spawnEntity(centeredLocation, EntityType.IRON_GOLEM) as IronGolem

        // Configure the Iron Golem
        entity.setAI(false) // Disable AI to prevent movement
        entity.isCustomNameVisible = true

        // Create colorful name tag with Component API
        val nameComponent = Component.text("Join ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(challenge.name, NamedTextColor.AQUA))
        entity.customName(nameComponent)

        entity.isInvulnerable = true

        // Mark as NPC using persistent data
        entity.persistentDataContainer.set(npcKey, PersistentDataType.BYTE, 1)
        entity.persistentDataContainer.set(npcIdKey, PersistentDataType.STRING, npcId.toString())

        // Save to database
        val query = """
            INSERT INTO challenge_npcs
            (id, challenge_id, world_name, x, y, z, yaw, pitch, entity_uuid, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """.trimIndent()

        plugin.databaseDriver.executeUpdate(
            query,
            npcId.toString(),
            challengeId.toString(),
            centeredLocation.world?.name ?: "",
            centeredLocation.x,
            centeredLocation.y,
            centeredLocation.z,
            centeredLocation.yaw.toDouble(),
            centeredLocation.pitch.toDouble(),
            entity.uniqueId.toString()
        )

        plugin.logger.info("Created NPC $npcId for challenge ${challenge.name} (${challenge.id})")
        return npcId
    }

    /**
     * Delete an NPC by its ID
     */
    fun deleteNPC(npcId: UUID): Boolean {
        // Get NPC data first
        val npc = getNPCById(npcId) ?: return false

        // Remove the entity if it exists
        npc.entityUuid?.let { entityUuid ->
            plugin.server.worlds.forEach { world ->
                world.entities.find { it.uniqueId == entityUuid }?.remove()
            }
        }

        // Delete from database
        val query = "DELETE FROM challenge_npcs WHERE id = ?"
        plugin.databaseDriver.executeUpdate(query, npcId.toString())

        plugin.logger.info("Deleted NPC $npcId")
        return true
    }

    /**
     * Delete all NPCs associated with a challenge
     */
    fun deleteNPCsForChallenge(challengeId: UUID) {
        // Get all NPCs for this challenge
        val npcs = getNPCsForChallenge(challengeId)

        // Remove all entities
        npcs.forEach { npc ->
            npc.entityUuid?.let { entityUuid ->
                plugin.server.worlds.forEach { world ->
                    world.entities.find { it.uniqueId == entityUuid }?.remove()
                }
            }
        }

        // Delete from database (CASCADE will handle this, but explicit is clearer)
        val query = "DELETE FROM challenge_npcs WHERE challenge_id = ?"
        plugin.databaseDriver.executeUpdate(query, challengeId.toString())

        plugin.logger.info("Deleted ${npcs.size} NPCs for challenge $challengeId")
    }

    /**
     * Get an NPC by its ID
     */
    fun getNPCById(npcId: UUID): ChallengeNPC? {
        val query = "SELECT * FROM challenge_npcs WHERE id = ?"
        return plugin.databaseDriver.executeQuery(query, npcId.toString()) { rs ->
            if (rs.next()) {
                val challengeId = UUID.fromString(rs.getString("challenge_id"))
                val worldName = rs.getString("world_name")
                val x = rs.getDouble("x")
                val y = rs.getDouble("y")
                val z = rs.getDouble("z")
                val yaw = rs.getFloat("yaw")
                val pitch = rs.getFloat("pitch")
                val entityUuidStr = rs.getString("entity_uuid")

                val world = plugin.server.getWorld(worldName)
                val location = world?.let { Location(it, x, y, z, yaw, pitch) }
                val entityUuid = entityUuidStr?.let { UUID.fromString(it) }

                if (location != null) {
                    ChallengeNPC(npcId, challengeId, location, entityUuid)
                } else null
            } else null
        }
    }

    /**
     * Get NPC data by entity UUID
     */
    fun getNPCByEntityUuid(entityUuid: UUID): ChallengeNPC? {
        val query = "SELECT * FROM challenge_npcs WHERE entity_uuid = ?"
        return plugin.databaseDriver.executeQuery(query, entityUuid.toString()) { rs ->
            if (rs.next()) {
                val npcId = UUID.fromString(rs.getString("id"))
                val challengeId = UUID.fromString(rs.getString("challenge_id"))
                val worldName = rs.getString("world_name")
                val x = rs.getDouble("x")
                val y = rs.getDouble("y")
                val z = rs.getDouble("z")
                val yaw = rs.getFloat("yaw")
                val pitch = rs.getFloat("pitch")

                val world = plugin.server.getWorld(worldName)
                val location = world?.let { Location(it, x, y, z, yaw, pitch) }

                if (location != null) {
                    ChallengeNPC(npcId, challengeId, location, entityUuid)
                } else null
            } else null
        }
    }

    /**
     * Get all NPCs for a challenge
     */
    fun getNPCsForChallenge(challengeId: UUID): List<ChallengeNPC> {
        val query = "SELECT * FROM challenge_npcs WHERE challenge_id = ?"
        return plugin.databaseDriver.executeQuery(query, challengeId.toString()) { rs ->
            val npcs = mutableListOf<ChallengeNPC>()
            while (rs.next()) {
                val npcId = UUID.fromString(rs.getString("id"))
                val worldName = rs.getString("world_name")
                val x = rs.getDouble("x")
                val y = rs.getDouble("y")
                val z = rs.getDouble("z")
                val yaw = rs.getFloat("yaw")
                val pitch = rs.getFloat("pitch")
                val entityUuidStr = rs.getString("entity_uuid")

                val world = plugin.server.getWorld(worldName)
                val location = world?.let { Location(it, x, y, z, yaw, pitch) }
                val entityUuid = entityUuidStr?.let { UUID.fromString(it) }

                if (location != null) {
                    npcs.add(ChallengeNPC(npcId, challengeId, location, entityUuid))
                }
            }
            npcs
        } ?: emptyList()
    }

    /**
     * Load all NPCs from the database and spawn them in the world
     */
    fun loadNPCs() {
        val query = "SELECT * FROM challenge_npcs"
        val loadedCount = plugin.databaseDriver.executeQuery(query) { rs ->
            var count = 0
            while (rs.next()) {
                val npcId = UUID.fromString(rs.getString("id"))
                val challengeId = UUID.fromString(rs.getString("challenge_id"))
                val worldName = rs.getString("world_name")
                val x = rs.getDouble("x")
                val y = rs.getDouble("y")
                val z = rs.getDouble("z")
                val yaw = rs.getFloat("yaw")
                val pitch = rs.getFloat("pitch")

                val world = plugin.server.getWorld(worldName)
                if (world == null) {
                    plugin.logger.warning("Cannot load NPC $npcId: World $worldName not found")
                    continue
                }

                // Check if challenge still exists
                val challenge = plugin.challengeManager.getChallenge(challengeId)
                if (challenge == null) {
                    plugin.logger.warning("NPC $npcId references non-existent challenge $challengeId, skipping")
                    continue
                }

                val location = Location(world, x, y, z, yaw, pitch)

                // Spawn the Iron Golem
                val entity = world.spawnEntity(location, EntityType.IRON_GOLEM) as IronGolem

                // Configure the Iron Golem
                entity.setAI(false)
                entity.isCustomNameVisible = true

                // Create colorful name tag with Component API
                val nameComponent = Component.text("Join ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(challenge.name, NamedTextColor.AQUA))
                entity.customName(nameComponent)

                entity.isInvulnerable = true

                // Mark as NPC
                entity.persistentDataContainer.set(npcKey, PersistentDataType.BYTE, 1)
                entity.persistentDataContainer.set(npcIdKey, PersistentDataType.STRING, npcId.toString())

                // Update entity UUID in database
                val updateQuery = "UPDATE challenge_npcs SET entity_uuid = ? WHERE id = ?"
                plugin.databaseDriver.executeUpdate(updateQuery, entity.uniqueId.toString(), npcId.toString())

                count++
            }
            count
        } ?: 0

        plugin.logger.info("Loaded $loadedCount challenge NPCs")
    }

    /**
     * Check if an entity is a challenge NPC
     */
    fun isNPC(entity: org.bukkit.entity.Entity): Boolean {
        if (entity !is IronGolem) return false
        return entity.persistentDataContainer.has(npcKey, PersistentDataType.BYTE)
    }

    /**
     * Get the NPC ID from an entity
     */
    fun getNPCId(entity: org.bukkit.entity.Entity): UUID? {
        if (!isNPC(entity)) return null
        val idString = (entity as IronGolem).persistentDataContainer.get(npcIdKey, PersistentDataType.STRING)
        return idString?.let { UUID.fromString(it) }
    }
}
