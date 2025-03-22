package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.io.File
import java.util.UUID
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages player data persistence between sessions for challenges.
 * Stores inventory contents, armor contents, ender chest contents, and location.
 * Each player can have multiple saved states - one for each challenge they participate in.
 */
class PlayerDataManager(private val plugin: ChallengePluginPlugin) {

    private val dataFolder = File(plugin.dataFolder, "playerdata")

    // Maps player UUID to a map of challenge UUIDs to player data
    private val cachedPlayerData = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PlayerData>>()

    init {
        dataFolder.mkdirs()
    }

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

        // Save to file
        saveToFile(playerData)

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

        // If not in cache, load from file
        if (playerData == null) {
            playerData = loadFromFile(uuid, challengeId)
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
        removeDataFile(uuid, challengeId)

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

        // Check file
        val file = File(dataFolder, "${playerId}_${challengeId}.yml")
        return file.exists()
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
                removeDataFile(playerId, challengeId)
            }

            // Clear all data for a specific player
            playerId != null -> {
                cachedPlayerData.remove(playerId)
                dataFolder.listFiles()?.forEach { file ->
                    if (file.name.startsWith("${playerId}_") && file.extension == "yml") {
                        file.delete()
                    }
                }
            }

            // Clear all data for a specific challenge
            challengeId != null -> {
                // Remove from cache
                cachedPlayerData.forEach { (_, challengeMap) ->
                    challengeMap.remove(challengeId)
                }

                // Remove files
                dataFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith("_${challengeId}.yml")) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun saveToFile(playerData: PlayerData) {
        val file = File(dataFolder, "${playerData.uuid}_${playerData.challengeId}.yml")
        val config = YamlConfiguration()

        config.set("uuid", playerData.uuid.toString())
        config.set("challengeId", playerData.challengeId.toString())

        // Save inventory as Base64
        config.set("inventory", itemStackArrayToBase64(playerData.inventory))
        config.set("armor", itemStackArrayToBase64(playerData.armor))
        config.set("enderChest", itemStackArrayToBase64(playerData.enderChest))

        // Save location
        config.set("location.world", playerData.location.world?.name)
        config.set("location.x", playerData.location.x)
        config.set("location.y", playerData.location.y)
        config.set("location.z", playerData.location.z)
        config.set("location.yaw", playerData.location.yaw)
        config.set("location.pitch", playerData.location.pitch)

        // Save player stats
        config.set("exp", playerData.exp)
        config.set("level", playerData.level)
        config.set("health", playerData.health)
        config.set("foodLevel", playerData.foodLevel)
        config.set("gameMode", playerData.gameMode.name)

        // Save potion effects
        val potionEffects = mutableListOf<Map<String, Any>>()
        playerData.potionEffects.forEach { effect ->
            val effectMap = mutableMapOf<String, Any>(
                "type" to effect.type.name,
                "duration" to effect.duration,
                "amplifier" to effect.amplifier,
                "ambient" to effect.isAmbient,
                "particles" to effect.hasParticles(),
                "icon" to effect.hasIcon()
            )
            potionEffects.add(effectMap)
        }
        config.set("potionEffects", potionEffects)

        config.save(file)
    }

    private fun loadFromFile(playerId: UUID, challengeId: UUID): PlayerData? {
        val file = File(dataFolder, "${playerId}_${challengeId}.yml")
        if (!file.exists()) {
            return null
        }

        val config = YamlConfiguration.loadConfiguration(file)

        // Get inventory items
        val inventoryBase64 = config.getString("inventory") ?: return null
        val armorBase64 = config.getString("armor") ?: return null
        val enderChestBase64 = config.getString("enderChest") ?: return null

        val inventory = try {
            base64ToItemStackArray(inventoryBase64)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load inventory for player $playerId: ${e.message}")
            return null
        }

        val armor = try {
            base64ToItemStackArray(armorBase64)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load armor for player $playerId: ${e.message}")
            return null
        }

        val enderChest = try {
            base64ToItemStackArray(enderChestBase64)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load ender chest for player $playerId: ${e.message}")
            return null
        }

        // Get world location
        val worldName = config.getString("location.world") ?: return null
        val world = Bukkit.getWorld(worldName)

        if (world == null) {
            plugin.logger.warning("World $worldName not found for player $playerId")
            return null
        }

        val location = Location(
            world,
            config.getDouble("location.x"),
            config.getDouble("location.y"),
            config.getDouble("location.z"),
            config.getDouble("location.yaw").toFloat(),
            config.getDouble("location.pitch").toFloat()
        )

        // Get GameMode or default to SURVIVAL
        val gameModeStr = config.getString("gameMode")
        val gameMode = if (gameModeStr != null) {
            try {
                GameMode.valueOf(gameModeStr)
            } catch (e: Exception) {
                plugin.logger.warning("Invalid GameMode value: $gameModeStr")
                GameMode.SURVIVAL
            }
        } else {
            GameMode.SURVIVAL
        }

        // Get potion effects
        val potionEffects = mutableListOf<PotionEffect>()
        if (config.contains("potionEffects")) {
            val effectsList = config.getList("potionEffects")
            effectsList?.forEach { effectObj ->
                if (effectObj is Map<*, *>) {
                    try {
                        val type = PotionEffectType.getByName(effectObj["type"] as? String ?: "")
                        if (type != null) {
                            val duration = (effectObj["duration"] as? Int) ?: 0
                            val amplifier = (effectObj["amplifier"] as? Int) ?: 0
                            val ambient = (effectObj["ambient"] as? Boolean) ?: false
                            val particles = (effectObj["particles"] as? Boolean) ?: true
                            val icon = (effectObj["icon"] as? Boolean) ?: true

                            potionEffects.add(PotionEffect(
                                type,
                                duration,
                                amplifier,
                                ambient,
                                particles,
                                icon
                            ))
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load potion effect: ${e.message}")
                    }
                }
            }
        }

        return PlayerData(
            uuid = playerId,
            challengeId = challengeId,
            inventory = inventory,
            armor = armor,
            enderChest = enderChest,
            location = location,
            exp = config.getDouble("exp").toFloat(),
            level = config.getInt("level"),
            health = config.getDouble("health"),
            foodLevel = config.getInt("foodLevel"),
            gameMode = gameMode,
            potionEffects = potionEffects
        )
    }

    private fun removeDataFile(playerId: UUID, challengeId: UUID) {
        val file = File(dataFolder, "${playerId}_${challengeId}.yml")
        if (file.exists()) {
            file.delete()
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
