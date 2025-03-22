package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import li.angu.challengeplugin.models.ChallengeSettings
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.GameRule
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.Duration
import java.time.Instant

class ChallengeManager(private val plugin: ChallengePluginPlugin) {

    private val activeChallenges = ConcurrentHashMap<UUID, Challenge>()
    private val playerChallengeMap = ConcurrentHashMap<UUID, UUID>()
    private val dataFolder = File(plugin.dataFolder, "challenges")

    init {
        dataFolder.mkdirs()
        loadSavedChallenges()
    }

    private fun loadSavedChallenges() {
        dataFolder.listFiles()?.filter { it.isFile && it.extension == "yml" }?.forEach { file ->
            val config = YamlConfiguration.loadConfiguration(file)
            try {
                val id = UUID.fromString(file.nameWithoutExtension)

                // Load settings
                val settings = ChallengeSettings(
                    naturalRegeneration = config.getBoolean("settings.naturalRegeneration", true),
                    syncHearts = config.getBoolean("settings.syncHearts", false),
                    blockRandomizer = config.getBoolean("settings.blockRandomizer", false)
                )

                val challenge = Challenge(
                    id = id,
                    name = config.getString("name", "Unknown") ?: "Unknown",
                    worldName = config.getString("worldName", "world_${id}") ?: "world_${id}",
                    status = ChallengeStatus.valueOf(config.getString("status", "ACTIVE") ?: "ACTIVE"),
                    players = config.getStringList("players")
                        .map { UUID.fromString(it) }
                        .toMutableSet(),
                    settings = settings
                )

                // Load timestamps if they exist
                if (config.contains("startedAt")) {
                    challenge.startedAt = Instant.ofEpochSecond(config.getLong("startedAt"))
                }

                if (config.contains("completedAt")) {
                    challenge.completedAt = Instant.ofEpochSecond(config.getLong("completedAt"))
                }

                // Load pause data if it exists
                if (config.contains("pausedAt")) {
                    challenge.pausedAt = Instant.ofEpochSecond(config.getLong("pausedAt"))
                }

                if (config.contains("totalPausedDuration")) {
                    challenge.totalPausedDuration = Duration.ofSeconds(config.getLong("totalPausedDuration"))
                }

                if (config.contains("lastEmptyTimestamp")) {
                    challenge.lastEmptyTimestamp = Instant.ofEpochSecond(config.getLong("lastEmptyTimestamp"))
                }

                activeChallenges[id] = challenge

                // Update player map
                challenge.players.forEach { playerId ->
                    playerChallengeMap[playerId] = id
                }

                // Only load world if challenge has players or is recently empty
                if (challenge.players.isNotEmpty() || !challenge.isReadyForUnload()) {
                    loadWorld(challenge.worldName)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load challenge from file ${file.name}: ${e.message}")
            }
        }
    }

    fun saveActiveChallenges() {
        activeChallenges.values.forEach { challenge ->
            val file = File(dataFolder, "${challenge.id}.yml")
            val config = YamlConfiguration()

            config.set("name", challenge.name)
            config.set("worldName", challenge.worldName)
            config.set("status", challenge.status.name)
            config.set("players", challenge.players.map { it.toString() })
            config.set("createdAt", challenge.createdAt.epochSecond)
            challenge.startedAt?.let { config.set("startedAt", it.epochSecond) }
            challenge.completedAt?.let { config.set("completedAt", it.epochSecond) }

            // Save pause data
            challenge.pausedAt?.let { config.set("pausedAt", it.epochSecond) }
            config.set("totalPausedDuration", challenge.totalPausedDuration.seconds)
            challenge.lastEmptyTimestamp?.let { config.set("lastEmptyTimestamp", it.epochSecond) }

            // Save settings
            config.set("settings.naturalRegeneration", challenge.settings.naturalRegeneration)
            config.set("settings.syncHearts", challenge.settings.syncHearts)
            config.set("settings.blockRandomizer", challenge.settings.blockRandomizer)

            config.save(file)
        }
    }

    fun createChallenge(name: String): Challenge {
        val id = UUID.randomUUID()
        val worldName = "challenge_${id.toString().substring(0, 8)}"

        val challenge = Challenge(id, name, worldName)
        activeChallenges[id] = challenge

        // We'll create the world when the challenge is started after
        // settings have been configured

        return challenge
    }

    fun finalizeChallenge(challenge: Challenge): Boolean {
        // Create the world with the challenge settings
        val world = createHardcoreWorld(challenge.worldName, challenge)

        return world != null
    }

    fun getChallenge(id: UUID): Challenge? {
        return activeChallenges[id]
    }

    fun getAllChallenges(): List<Challenge> {
        return activeChallenges.values.toList()
    }

    fun getActiveChallenges(): List<Challenge> {
        return activeChallenges.values.filter { it.status == ChallengeStatus.ACTIVE }
    }

    fun getPlayerChallenge(player: Player): Challenge? {
        val challengeId = playerChallengeMap[player.uniqueId] ?: return null
        return activeChallenges[challengeId]
    }

    fun removeChallenge(challengeId: UUID) {
        activeChallenges.remove(challengeId)
    }
    
    /**
     * Deletes a challenge and its world folders.
     * Returns true if successful, false otherwise.
     */
    fun deleteChallenge(challengeId: UUID): Boolean {
        val challenge = getChallenge(challengeId) ?: return false
        
        // Don't delete if players are still in the challenge
        if (challenge.players.isNotEmpty()) {
            // Check if any of the players are online
            val hasOnlinePlayers = challenge.players.any { playerId ->
                plugin.server.getPlayer(playerId) != null
            }
            
            if (hasOnlinePlayers) {
                return false
            }
        }
        
        // Get world names
        val mainWorldName = challenge.worldName
        val netherWorldName = "${mainWorldName}_nether"
        val endWorldName = "${mainWorldName}_the_end"
        
        // Unload worlds if they're loaded
        unloadWorld(mainWorldName)
        unloadWorld(netherWorldName)
        unloadWorld(endWorldName)
        
        // Delete challenge data file
        val challengeFile = File(dataFolder, "${challenge.id}.yml")
        if (challengeFile.exists()) {
            challengeFile.delete()
        }
        
        // Remove from activeChallenges
        activeChallenges.remove(challengeId)
        
        // Remove from playerChallengeMap
        challenge.players.forEach { playerId ->
            playerChallengeMap.remove(playerId)
        }
        
        // Delete world folders
        val serverDir = plugin.server.worldContainer
        val worldsDeleted = deleteWorldFolder(File(serverDir, mainWorldName)) &&
                       deleteWorldFolder(File(serverDir, netherWorldName)) &&
                       deleteWorldFolder(File(serverDir, endWorldName))
        
        return worldsDeleted
    }
    
    /**
     * Unloads a world properly.
     */
    private fun unloadWorld(worldName: String): Boolean {
        val world = Bukkit.getWorld(worldName) ?: return true
        
        // Teleport any players out of this world
        world.players.forEach { player ->
            val mainWorld = Bukkit.getWorlds().firstOrNull()
            if (mainWorld != null) {
                player.teleport(mainWorld.spawnLocation)
            }
        }
        
        // Unload the world
        return Bukkit.unloadWorld(world, false)
    }
    
    /**
     * Recursively deletes a world folder.
     */
    private fun deleteWorldFolder(worldFolder: File): Boolean {
        if (!worldFolder.exists()) {
            return true
        }
        
        try {
            // Delete all files and subdirectories
            worldFolder.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteWorldFolder(file)
                } else {
                    file.delete()
                }
            }
            
            // Delete the world folder itself
            return worldFolder.delete()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to delete world folder ${worldFolder.name}: ${e.message}")
            return false
        }
    }

    /**
     * Map a player to a challenge without resetting inventory or teleporting
     * Used for reconnection logic
     */
    fun addPlayerToChallenge(playerId: UUID, challengeId: UUID) {
        playerChallengeMap[playerId] = challengeId

        // This method is called during reconnection, so we need to make sure
        // the player is also present in the challenge's player set
        val challenge = activeChallenges[challengeId]
        if (challenge != null) {
            // We don't use challenge.addPlayer since that's meant for Player objects
            // But we want to make sure the player UUID is in the challenge's set
            challenge.players.add(playerId)

            // If this is the first player, resume the timer if it was paused
            if (challenge.players.size == 1 && challenge.pausedAt != null) {
                challenge.resumeTimer()
                challenge.lastEmptyTimestamp = null
            }
        }
    }

    fun joinChallenge(player: Player, challenge: Challenge): Boolean {
        // Check if player is already in a challenge
        val currentChallenge = getPlayerChallenge(player)
        if (currentChallenge != null) {
            if (currentChallenge.id == challenge.id) {
                return false // Already in this challenge
            }
            leaveChallenge(player)
        }

        // Check if player has saved data for this challenge
        if (plugin.playerDataManager.hasPlayerData(player.uniqueId, challenge.id)) {
            // Restore player data (inventory, location, etc.)
            if (plugin.playerDataManager.restorePlayerData(player, challenge.id)) {
                // Add player to challenge without resetting
                if (challenge.addPlayer(player)) {
                    playerChallengeMap[player.uniqueId] = challenge.id
                    return true
                }
            }
        }

        // If no saved data or restore failed, add player normally
        if (challenge.addPlayer(player)) {
            playerChallengeMap[player.uniqueId] = challenge.id

            // Get the challenge world
            val world = Bukkit.getWorld(challenge.worldName) ?: loadWorld(challenge.worldName)
            if (world != null) {
                challenge.setupPlayerForChallenge(player, world)
                return true
            }
        }

        return false
    }

    fun leaveChallenge(player: Player): Boolean {
        val challenge = getPlayerChallenge(player) ?: return false

        // Note: Player data is now saved in the command handler
        // to prevent duplicate saving

        if (challenge.removePlayer(player)) {
            playerChallengeMap.remove(player.uniqueId)

            // Reset player state when leaving
            player.inventory.clear()
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }
            player.exp = 0f
            player.level = 0
            player.health = 20.0
            player.foodLevel = 20
            player.gameMode = org.bukkit.GameMode.CREATIVE

            // Teleport back to main world
            val mainWorld = Bukkit.getWorlds().firstOrNull()
            if (mainWorld != null) {
                player.teleport(mainWorld.spawnLocation)
            }

            return true
        }

        return false
    }

    fun completeChallenge(challengeId: UUID) {
        val challenge = activeChallenges[challengeId] ?: return

        challenge.complete()

        // Handle rewards or other completion logic
        Bukkit.getOnlinePlayers().forEach { player ->
            if (challenge.isPlayerInChallenge(player)) {
                player.sendMessage(
                    plugin.languageManager.getMessage("challenge.completed", player, "name" to challenge.name)
                )
            }
        }
    }

    private fun createHardcoreWorld(worldName: String, challenge: Challenge? = null): World? {
        // Create overworld
        val creator = WorldCreator(worldName)
        val world = creator.createWorld() ?: return null
        val worldSeed = world.seed

        // Set game rules (we'll apply full settings after challenge setup)
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        world.difficulty = org.bukkit.Difficulty.HARD

        // If we have a challenge already, apply settings
        if (challenge != null) {
            challenge.applySettingsToWorld(world)
        }

        // Create nether world
        val netherName = "${worldName}_nether"
        val netherCreator = WorldCreator(netherName)
            .environment(World.Environment.NETHER)
            .seed(worldSeed) // Use the same seed as main world
        val netherWorld = netherCreator.createWorld()
        if (netherWorld != null) {
            // Apply game rules
            netherWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
            netherWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
            netherWorld.difficulty = org.bukkit.Difficulty.HARD

            // Apply challenge settings if available
            if (challenge != null) {
                challenge.applySettingsToWorld(netherWorld)
            }

            plugin.logger.info("Created nether world: $netherName with seed $worldSeed")
        } else {
            plugin.logger.warning("Failed to create nether world: $netherName")
        }

        // Create end world
        val endName = "${worldName}_the_end"
        val endCreator = WorldCreator(endName)
            .environment(World.Environment.THE_END)
            .seed(worldSeed) // Use the same seed as main world
        val endWorld = endCreator.createWorld()
        if (endWorld != null) {
            // Apply game rules
            endWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
            endWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
            endWorld.difficulty = org.bukkit.Difficulty.HARD

            // Apply challenge settings if available
            if (challenge != null) {
                challenge.applySettingsToWorld(endWorld)
            }

            plugin.logger.info("Created end world: $endName with seed $worldSeed")
        } else {
            plugin.logger.warning("Failed to create end world: $endName")
        }

        plugin.logger.info("Created new hardcore world set: $worldName (with nether and end)")
        return world
    }

    private fun loadWorld(worldName: String): World? {
        return try {
            val mainWorld = if (Bukkit.getWorld(worldName) == null) {
                Bukkit.createWorld(WorldCreator(worldName))
            } else {
                Bukkit.getWorld(worldName)
            }

            // Also load the associated nether and end worlds if they exist
            val netherName = "${worldName}_nether"
            if (Bukkit.getWorld(netherName) == null) {
                val netherCreator = WorldCreator(netherName)
                    .environment(World.Environment.NETHER)
                    .seed(mainWorld?.seed ?: 0) // Use the same seed as main world
                Bukkit.createWorld(netherCreator)
            }

            val endName = "${worldName}_the_end"
            if (Bukkit.getWorld(endName) == null) {
                val endCreator = WorldCreator(endName)
                    .environment(World.Environment.THE_END)
                    .seed(mainWorld?.seed ?: 0) // Use the same seed as main world
                Bukkit.createWorld(endCreator)
            }

            mainWorld
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load world set $worldName: ${e.message}")
            null
        }
    }
}
