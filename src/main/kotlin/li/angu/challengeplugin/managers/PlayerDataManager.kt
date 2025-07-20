package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.sql.Timestamp
import java.time.Instant

/**
 * Manages player data persistence between sessions for challenges.
 * Stores inventory contents, armor contents, ender chest contents, and location.
 * Each player can have multiple saved states - one for each challenge they participate in.
 */
class PlayerDataManager(private val plugin: ChallengePluginPlugin) {

    // Maps player UUID to a map of challenge UUIDs to player data
    private val cachedPlayerData = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PlayerData>>()

    /**
     * Saves a player's data when they disconnect during a challenge
     */
    fun savePlayerData(player: Player, challengeId: UUID) {
        val uuid = player.uniqueId
        val playerData = PlayerData(
            uuid = uuid,
            challengeId = challengeId,
            inventory = player.inventory.contents,
            armor = player.inventory.armorContents,
            enderChest = player.enderChest.contents,
            location = player.location,
            exp = player.exp,
            level = player.level,
            health = player.health,
            foodLevel = player.foodLevel,
            gameMode = player.gameMode,
            potionEffects = player.activePotionEffects.toList()
        )

        // Cache player data
        cachedPlayerData.computeIfAbsent(uuid) { ConcurrentHashMap() }[challengeId] = playerData

        // Save to database
        saveToDatabase(playerData)

        plugin.logger.info("Saved player data for ${player.name} in challenge $challengeId")
    }

    /**
     * Restores player data when they reconnect to a challenge
     * Returns true if data was restored, false otherwise
     */
    fun restorePlayerData(player: Player, challengeId: UUID): Boolean {
        val uuid = player.uniqueId

        // Check cache first
        var playerData = cachedPlayerData[uuid]?.get(challengeId)

        // If not in cache, load from database
        if (playerData == null) {
            playerData = loadFromDatabase(uuid, challengeId)
            if (playerData != null) {
                cachedPlayerData.computeIfAbsent(uuid) { ConcurrentHashMap() }[challengeId] = playerData
            }
        }

        // Make sure data exists for this challenge
        if (playerData == null) {
            return false
        }

        // Restore player state
        player.inventory.contents = playerData.inventory

        // Note: We can't directly set armor contents since it's a val property
        // Handle setting individual armor pieces instead
        playerData.armor.forEachIndexed { index, item ->
            when (index) {
                0 -> player.inventory.boots = item
                1 -> player.inventory.leggings = item
                2 -> player.inventory.chestplate = item
                3 -> player.inventory.helmet = item
            }
        }

        player.enderChest.contents = playerData.enderChest
        player.exp = playerData.exp
        player.level = playerData.level
        player.health = playerData.health
        player.foodLevel = playerData.foodLevel
        player.gameMode = playerData.gameMode

        // Clear existing effects and apply saved effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }
        playerData.potionEffects.forEach { effect ->
            player.addPotionEffect(effect)
        }

        // Make sure world is loaded before teleporting
        val world = Bukkit.getWorld(playerData.location.world?.name ?: return false)
        if (world != null) {
            val location = Location(
                world,
                playerData.location.x,
                playerData.location.y,
                playerData.location.z,
                playerData.location.yaw,
                playerData.location.pitch
            )
            player.teleport(location)
        }

        // Clear this specific challenge data after restoring
        cachedPlayerData[uuid]?.remove(challengeId)
        removeFromDatabase(uuid, challengeId)

        plugin.logger.info("Restored player data for ${player.name} in challenge $challengeId")
        return true
    }

    /**
     * Checks if a player has saved data for a challenge
     */
    fun hasPlayerData(playerId: UUID, challengeId: UUID): Boolean {
        // Check cache first
        if (cachedPlayerData[playerId]?.containsKey(challengeId) == true) {
            return true
        }

        // Check database
        val result = plugin.databaseDriver.executeQuery(
            "SELECT 1 FROM player_challenge_data WHERE player_uuid = ? AND challenge_id = ?",
            playerId.toString(),
            challengeId.toString()
        ) { rs ->
            rs.next()
        } ?: false
        
        return result
    }

    /**
     * Efficiently finds which challenge (if any) a player has saved data for.
     * Only queries the database once and returns the challenge object.
     * Returns null if no saved data exists.
     */
    fun findChallengeWithPlayerData(playerId: UUID): Challenge? {
        // First check cache for any cached data
        cachedPlayerData[playerId]?.let { challengeMap ->
            if (challengeMap.isNotEmpty()) {
                // Player has cached data, find which challenge it belongs to
                val challengeId = challengeMap.keys.first()
                return plugin.challengeManager.getChallenge(challengeId)
            }
        }

        // Query database once to find any challenge with player data
        val challengeId = plugin.databaseDriver.executeQuery(
            "SELECT challenge_id FROM player_challenge_data WHERE player_uuid = ? LIMIT 1",
            playerId.toString()
        ) { rs ->
            if (rs.next()) {
                try {
                    val id = rs.getString("challenge_id")
                    UUID.fromString(id)
                } catch (e: Exception) {
                    plugin.logger.warning("Invalid challenge_id UUID in player data: ${e.message}")
                    null
                }
            } else {
                null
            }
        }

        // Return the challenge object if found
        return challengeId?.let { 
            val challenge = plugin.challengeManager.getChallenge(it)
            if (challenge == null) {
                plugin.logger.warning("Challenge $it found in database but not loaded in memory for player $playerId")
            }
            challenge
        }
    }

    /**
     * Clears player data for a specific player-challenge combination,
     * all data for a specific player, or all data for a specific challenge
     */
    fun clearPlayerData(playerId: UUID? = null, challengeId: UUID? = null) {
        when {
            // Clear specific player-challenge combination
            playerId != null && challengeId != null -> {
                cachedPlayerData[playerId]?.remove(challengeId)
                removeFromDatabase(playerId, challengeId)
            }

            // Clear all data for a specific player
            playerId != null -> {
                cachedPlayerData.remove(playerId)
                val operations = listOf(
                    "DELETE FROM player_potion_effects WHERE player_uuid = ?" to arrayOf<Any?>(playerId.toString()),
                    "DELETE FROM player_challenge_data WHERE player_uuid = ?" to arrayOf<Any?>(playerId.toString())
                )
                plugin.databaseDriver.executeTransaction(operations)
            }

            // Clear all data for a specific challenge
            challengeId != null -> {
                // Remove from cache
                cachedPlayerData.forEach { (_, challengeMap) ->
                    challengeMap.remove(challengeId)
                }

                // Remove from database
                val operations = listOf(
                    "DELETE FROM player_potion_effects WHERE challenge_id = ?" to arrayOf<Any?>(challengeId.toString()),
                    "DELETE FROM player_challenge_data WHERE challenge_id = ?" to arrayOf<Any?>(challengeId.toString())
                )
                plugin.databaseDriver.executeTransaction(operations)
            }
        }
    }

    private fun saveToDatabase(playerData: PlayerData) {
        val operations = mutableListOf<Pair<String, Array<Any?>>>()

        // Save main player data (temporarily without armor to test)
        operations.add(
            "INSERT OR REPLACE INTO player_challenge_data " +
            "(player_uuid, challenge_id, inventory_data, ender_chest_data, location_world, " +
            "location_x, location_y, location_z, location_yaw, location_pitch, health, " +
            "food_level, experience_points, experience_level, game_mode, saved_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" to arrayOf<Any?>(
                playerData.uuid.toString(),
                playerData.challengeId.toString(),
                itemStackArrayToBase64(playerData.inventory),
                itemStackArrayToBase64(playerData.enderChest),
                playerData.location.world?.name ?: "",
                playerData.location.x,
                playerData.location.y,
                playerData.location.z,
                playerData.location.yaw.toDouble(),
                playerData.location.pitch.toDouble(),
                playerData.health,
                playerData.foodLevel,
                playerData.exp.toInt(),
                playerData.level,
                playerData.gameMode.name,
                Timestamp.from(Instant.now())
            )
        )

        // Clear existing potion effects
        operations.add(
            "DELETE FROM player_potion_effects WHERE player_uuid = ? AND challenge_id = ?" to arrayOf<Any?>(
                playerData.uuid.toString(),
                playerData.challengeId.toString()
            )
        )

        // Save potion effects
        playerData.potionEffects.forEach { effect ->
            operations.add(
                "INSERT INTO player_potion_effects " +
                "(player_uuid, challenge_id, effect_type, duration, amplifier, ambient, particles, icon) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)" to arrayOf<Any?>(
                    playerData.uuid.toString(),
                    playerData.challengeId.toString(),
                    effect.type.name,
                    effect.duration,
                    effect.amplifier,
                    effect.isAmbient,
                    effect.hasParticles(),
                    effect.hasIcon()
                )
            )
        }

        if (!plugin.databaseDriver.executeTransaction(operations)) {
            plugin.logger.severe("Failed to save player data to database for ${playerData.uuid}")
        }
    }

    private fun loadFromDatabase(playerId: UUID, challengeId: UUID): PlayerData? {
        try {
            // Load main player data
            val playerData = plugin.databaseDriver.executeQuery(
                """
                SELECT inventory_data, armor_data, ender_chest_data, location_world, location_x, location_y, location_z,
                       location_yaw, location_pitch, health, food_level, experience_points, experience_level, game_mode
                FROM player_challenge_data 
                WHERE player_uuid = ? AND challenge_id = ?
                """.trimIndent(),
                playerId.toString(),
                challengeId.toString()
            ) { rs ->
                if (rs.next()) {
                    val inventoryBase64 = rs.getString("inventory_data")
                    val armorBase64 = rs.getString("armor_data")
                    val enderChestBase64 = rs.getString("ender_chest_data")
                    
                    if (inventoryBase64 == null || enderChestBase64 == null) {
                        return@executeQuery null
                    }

                    val inventory = try {
                        base64ToItemStackArray(inventoryBase64)
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load inventory for player $playerId: ${e.message}")
                        return@executeQuery null
                    }

                    // Load armor data - handle backward compatibility with old data that doesn't have armor
                    val armor = if (armorBase64 != null) {
                        try {
                            base64ToItemStackArray(armorBase64)
                        } catch (e: Exception) {
                            plugin.logger.warning("Failed to load armor for player $playerId: ${e.message}")
                            arrayOfNulls(4) // Fallback to empty armor
                        }
                    } else {
                        arrayOfNulls(4) // Backward compatibility for old data
                    }

                    val enderChest = try {
                        base64ToItemStackArray(enderChestBase64)
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load ender chest for player $playerId: ${e.message}")
                        return@executeQuery null
                    }

                    // Get world location
                    val worldName = rs.getString("location_world")
                    if (worldName == null) {
                        return@executeQuery null
                    }
                    
                    val world = Bukkit.getWorld(worldName)
                    if (world == null) {
                        plugin.logger.warning("World $worldName not found for player $playerId")
                        return@executeQuery null
                    }

                    val location = Location(
                        world,
                        rs.getDouble("location_x"),
                        rs.getDouble("location_y"),
                        rs.getDouble("location_z"),
                        rs.getDouble("location_yaw").toFloat(),
                        rs.getDouble("location_pitch").toFloat()
                    )

                    // Get GameMode or default to SURVIVAL
                    val gameModeStr = rs.getString("game_mode")
                    val gameMode = try {
                        GameMode.valueOf(gameModeStr)
                    } catch (e: Exception) {
                        plugin.logger.warning("Invalid GameMode value: $gameModeStr")
                        GameMode.SURVIVAL
                    }

                    PlayerData(
                        uuid = playerId,
                        challengeId = challengeId,
                        inventory = inventory,
                        armor = armor,
                        enderChest = enderChest,
                        location = location,
                        exp = rs.getInt("experience_points").toFloat(),
                        level = rs.getInt("experience_level"),
                        health = rs.getDouble("health"),
                        foodLevel = rs.getInt("food_level"),
                        gameMode = gameMode,
                        potionEffects = emptyList() // Will be loaded separately
                    )
                } else {
                    null
                }
            }

            if (playerData == null) return null

            // Load potion effects
            val potionEffects = mutableListOf<PotionEffect>()
            plugin.databaseDriver.executeQuery(
                """
                SELECT effect_type, duration, amplifier, ambient, particles, icon
                FROM player_potion_effects 
                WHERE player_uuid = ? AND challenge_id = ?
                """.trimIndent(),
                playerId.toString(),
                challengeId.toString()
            ) { rs ->
                while (rs.next()) {
                    try {
                        val type = PotionEffectType.getByName(rs.getString("effect_type"))
                        if (type != null) {
                            potionEffects.add(PotionEffect(
                                type,
                                rs.getInt("duration"),
                                rs.getInt("amplifier"),
                                rs.getBoolean("ambient"),
                                rs.getBoolean("particles"),
                                rs.getBoolean("icon")
                            ))
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load potion effect: ${e.message}")
                    }
                }
            }

            return playerData.copy(potionEffects = potionEffects)

        } catch (e: Exception) {
            plugin.logger.severe("Failed to load player data from database for $playerId: ${e.message}")
            return null
        }
    }

    private fun removeFromDatabase(playerId: UUID, challengeId: UUID) {
        val operations = listOf(
            "DELETE FROM player_potion_effects WHERE player_uuid = ? AND challenge_id = ?" to arrayOf<Any?>(
                playerId.toString(),
                challengeId.toString()
            ),
            "DELETE FROM player_challenge_data WHERE player_uuid = ? AND challenge_id = ?" to arrayOf<Any?>(
                playerId.toString(),
                challengeId.toString()
            )
        )

        if (!plugin.databaseDriver.executeTransaction(operations)) {
            plugin.logger.warning("Failed to remove player data from database for $playerId")
        }
    }

    /**
     * Converts an ItemStack array to a Base64 string for storage
     */
    private fun itemStackArrayToBase64(items: Array<ItemStack?>): String {
        try {
            ByteArrayOutputStream().use { outputStream ->
                BukkitObjectOutputStream(outputStream).use { dataOutput ->
                    dataOutput.writeInt(items.size)

                    for (item in items) {
                        dataOutput.writeObject(item)
                    }

                    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Unable to save item stacks", e)
        }
    }

    /**
     * Converts a Base64 string to an ItemStack array
     */
    private fun base64ToItemStackArray(data: String): Array<ItemStack?> {
        try {
            val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(data))
            val dataInput = BukkitObjectInputStream(inputStream)
            val items = arrayOfNulls<ItemStack>(dataInput.readInt())

            for (i in items.indices) {
                items[i] = dataInput.readObject() as? ItemStack
            }

            dataInput.close()
            return items
        } catch (e: Exception) {
            throw IllegalStateException("Unable to decode item stacks", e)
        }
    }

    /**
     * Class to store player data
     */
    data class PlayerData(
        val uuid: UUID,
        val challengeId: UUID,
        val inventory: Array<ItemStack?>,
        val armor: Array<ItemStack?>,
        val enderChest: Array<ItemStack?>,
        val location: Location,
        val exp: Float,
        val level: Int,
        val health: Double,
        val foodLevel: Int,
        val gameMode: GameMode = GameMode.SURVIVAL,
        val potionEffects: List<PotionEffect> = emptyList()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PlayerData

            if (uuid != other.uuid) return false
            if (challengeId != other.challengeId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = uuid.hashCode()
            result = 31 * result + challengeId.hashCode()
            return result
        }
    }
}
