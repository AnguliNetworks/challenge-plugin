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
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.io.File

class ChallengeManager(private val plugin: ChallengePluginPlugin) {

    private val activeChallenges = ConcurrentHashMap<UUID, Challenge>()
    private val playerChallengeMap = ConcurrentHashMap<UUID, UUID>()
    private val worldPreparationManager = WorldPreparationManager(plugin)

    init {
        loadSavedChallenges()
    }

    private fun loadSavedChallenges() {
        try {
            plugin.logger.info("Loading saved challenges from database...")
            // Load all challenges from database
            val challengeQuery = """
                SELECT c.id, c.name, c.world_name, c.creator_uuid, c.status, c.created_at, c.started_at, 
                       c.completed_at, c.paused_at, c.last_empty_timestamp, c.total_paused_duration,
                       cs.natural_regeneration, cs.sync_hearts, cs.block_randomizer, 
                       cs.level_world_border, cs.starter_kit, cs.border_size
                FROM challenges c
                LEFT JOIN challenge_settings cs ON c.id = cs.challenge_id
            """.trimIndent()

            plugin.databaseDriver.executeQuery(challengeQuery) { rs ->
                while (rs.next()) {
                    try {
                        val id = UUID.fromString(rs.getString("id"))

                        // Load settings
                        val starterKitName = rs.getString("starter_kit") ?: "NONE"
                        val settings = ChallengeSettings(
                            naturalRegeneration = rs.getBoolean("natural_regeneration"),
                            syncHearts = rs.getBoolean("sync_hearts"),
                            blockRandomizer = rs.getBoolean("block_randomizer"),
                            starterKit = try {
                                li.angu.challengeplugin.models.StarterKit.valueOf(starterKitName)
                            } catch (e: IllegalArgumentException) {
                                li.angu.challengeplugin.models.StarterKit.NONE
                            },
                            levelWorldBorder = rs.getBoolean("level_world_border"),
                            borderSize = rs.getDouble("border_size")
                        )

                        val challenge = Challenge(
                            id = id,
                            name = rs.getString("name"),
                            worldName = rs.getString("world_name") ?: "world_${id}",
                            creatorUuid = UUID.fromString(rs.getString("creator_uuid")),
                            createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                            status = ChallengeStatus.valueOf(rs.getString("status") ?: "ACTIVE"),
                            players = mutableSetOf(),
                            settings = settings
                        )

                        // Load timestamps
                        rs.getTimestamp("started_at")?.let {
                            challenge.startedAt = it.toInstant()
                        }

                        rs.getTimestamp("completed_at")?.let {
                            challenge.completedAt = it.toInstant()
                        }

                        rs.getTimestamp("paused_at")?.let {
                            challenge.pausedAt = it.toInstant()
                        }

                        rs.getTimestamp("last_empty_timestamp")?.let {
                            challenge.lastEmptyTimestamp = it.toInstant()
                        }

                        val pausedDurationSeconds = rs.getLong("total_paused_duration")
                        challenge.totalPausedDuration = Duration.ofSeconds(pausedDurationSeconds)

                        activeChallenges[id] = challenge
                        plugin.logger.info("Loaded challenge: ${challenge.name} (${challenge.id})")

                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load challenge from database: ${e.message}")
                    }
                }
            }
            
            plugin.logger.info("Loaded ${activeChallenges.size} challenges from database")

            // Load players for each challenge
            val playersQuery = """
                SELECT challenge_id, player_uuid 
                FROM challenge_participants
            """.trimIndent()

            plugin.databaseDriver.executeQuery(playersQuery) { rs ->
                while (rs.next()) {
                    try {
                        val challengeId = UUID.fromString(rs.getString("challenge_id"))
                        val playerId = UUID.fromString(rs.getString("player_uuid"))

                        val challenge = activeChallenges[challengeId]
                        if (challenge != null) {
                            challenge.players.add(playerId)
                            playerChallengeMap[playerId] = challengeId
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load challenge participant: ${e.message}")
                    }
                }
            }

            // Load worlds for challenges that need them
            activeChallenges.values.forEach { challenge ->
                if (challenge.players.isNotEmpty() || !challenge.isReadyForUnload()) {
                    loadWorld(challenge.worldName)
                }
            }

        } catch (e: Exception) {
            plugin.logger.severe("Failed to load challenges from database: ${e.message}")
        }
    }

    fun saveActiveChallenges() {
        activeChallenges.values.forEach { challenge ->
            if (!saveChallengeToDatabase(challenge)) {
                plugin.logger.warning("Failed to save challenge ${challenge.id} during shutdown")
            }
        }
    }

    private fun challengeExistsInDatabase(challengeId: UUID): Boolean {
        return plugin.databaseDriver.executeQuery(
            "SELECT 1 FROM challenges WHERE id = ?",
            challengeId.toString()
        ) { rs ->
            rs.next()
        } ?: false
    }

    private fun challengeSettingsExistInDatabase(challengeId: UUID): Boolean {
        return plugin.databaseDriver.executeQuery(
            "SELECT 1 FROM challenge_settings WHERE challenge_id = ?",
            challengeId.toString()
        ) { rs ->
            rs.next()
        } ?: false
    }

    fun saveChallengeToDatabase(challenge: Challenge): Boolean {
        val operations = mutableListOf<Pair<String, Array<Any?>>>()

        // Check if challenge exists and use appropriate query
        val challengeExists = challengeExistsInDatabase(challenge.id)
        val challengeQuery = if (challengeExists) {
            """
            UPDATE challenges SET 
            name = ?, world_name = ?, creator_uuid = ?, status = ?, created_at = ?, 
            started_at = ?, completed_at = ?, paused_at = ?, last_empty_timestamp = ?, total_paused_duration = ?
            WHERE id = ?
            """.trimIndent()
        } else {
            """
            INSERT INTO challenges 
            (name, world_name, creator_uuid, status, created_at, started_at, completed_at, paused_at, last_empty_timestamp, total_paused_duration, id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        }

        operations.add(challengeQuery to arrayOf<Any?>(
            challenge.name,
            challenge.worldName,
            challenge.creatorUuid.toString(),
            challenge.status.name,
            Timestamp.from(challenge.createdAt),
            challenge.startedAt?.let { Timestamp.from(it) },
            challenge.completedAt?.let { Timestamp.from(it) },
            challenge.pausedAt?.let { Timestamp.from(it) },
            challenge.lastEmptyTimestamp?.let { Timestamp.from(it) },
            challenge.totalPausedDuration.seconds,
            challenge.id.toString()
        ))

        // Save or update challenge settings - use UPDATE to avoid cascade deletes
        // Check if challenge settings exist and use appropriate query
        val settingsExist = challengeSettingsExistInDatabase(challenge.id)
        val settingsQuery = if (settingsExist) {
            """
            UPDATE challenge_settings SET 
            natural_regeneration = ?, sync_hearts = ?, block_randomizer = ?, 
            level_world_border = ?, starter_kit = ?, border_size = ?
            WHERE challenge_id = ?
            """.trimIndent()
        } else {
            """
            INSERT INTO challenge_settings 
            (natural_regeneration, sync_hearts, block_randomizer, level_world_border, starter_kit, border_size, challenge_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        }

        operations.add(settingsQuery to arrayOf<Any?>(
            challenge.settings.naturalRegeneration,
            challenge.settings.syncHearts,
            challenge.settings.blockRandomizer,
            challenge.settings.levelWorldBorder,
            challenge.settings.starterKit.name,
            challenge.settings.borderSize,
            challenge.id.toString()
        ))

        // Clear existing participants
        operations.add("DELETE FROM challenge_participants WHERE challenge_id = ?" to arrayOf<Any?>(challenge.id.toString()))

        // Add current participants
        challenge.players.forEach { playerId ->
            operations.add("INSERT INTO challenge_participants (challenge_id, player_uuid) VALUES (?, ?)" to arrayOf<Any?>(
                challenge.id.toString(),
                playerId.toString()
            ))
        }

        // Execute all operations in a transaction
        val success = plugin.databaseDriver.executeTransaction(operations)
        if (!success) {
            plugin.logger.severe("Failed to save challenge ${challenge.id} to database")
        }
        return success
    }

    fun createChallenge(name: String, creator: Player): Challenge {
        val id = UUID.randomUUID()
        val worldName = "challenge_${id.toString().substring(0, 8)}"

        val challenge = Challenge(id, name, worldName, creator.uniqueId)

        // Save to database first, only add to memory if successful
        if (saveChallengeToDatabase(challenge)) {
            activeChallenges[id] = challenge
        } else {
            throw RuntimeException("Failed to save challenge to database")
        }

        // We'll create the world when the challenge is started after
        // settings have been configured

        return challenge
    }

    fun finalizeChallenge(challenge: Challenge): Boolean {
        // Check if we have a pregenerated world available
        val preparedWorldName = worldPreparationManager.getWorldForChallenge(challenge.id)

        if (preparedWorldName != null) {
            // Update the challenge with the prepared world name
            challenge.worldName = preparedWorldName

            // Load the prepared world
            val world = loadWorld(preparedWorldName)

            // Apply challenge settings to the world
            if (world != null) {
                challenge.applySettingsToWorld(world)

                // Also apply settings to nether and end worlds
                challenge.getNetherWorld()?.let { challenge.applySettingsToWorld(it) }
                challenge.getEndWorld()?.let { challenge.applySettingsToWorld(it) }

                return true
            }
        }

        // If no prepared world is available or loading failed, create a new world
        plugin.logger.info("No pregenerated world available, creating a new world for challenge ${challenge.id}")
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

        // Delete challenge data from database
        val deleteOperations = listOf(
            "DELETE FROM challenge_participants WHERE challenge_id = ?" to arrayOf<Any?>(challenge.id.toString()),
            "DELETE FROM challenge_settings WHERE challenge_id = ?" to arrayOf<Any?>(challenge.id.toString()),
            "DELETE FROM challenges WHERE id = ?" to arrayOf<Any?>(challenge.id.toString())
        )

        if (!plugin.databaseDriver.executeTransaction(deleteOperations)) {
            plugin.logger.warning("Failed to delete challenge ${challenge.id} from database")
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

        // For each player in the world, leave challenge and teleport to lobby
        world.players.forEach { player ->
            val challenge = getPlayerChallenge(player)
            if (challenge != null) {
                // Save player data before leaving
                plugin.playerDataManager.savePlayerData(player, challenge.id)

                // Leave the challenge
                leaveChallenge(player)
            }

            // Teleport to lobby
            plugin.lobbyManager.teleportToLobby(player)
            player.sendMessage(plugin.languageManager.getMessage("lobby.teleported", player))
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

                    // Initialize player's world border if the feature is enabled
                    if (challenge.settings.levelWorldBorder) {
                        plugin.experienceBorderListener.initializeWorldBordersForPlayers(challenge)
                    }

                    return true
                }
            } else {
                plugin.logger.warning("Failed to restore player data for ${player.name} in challenge ${challenge.name}")
            }
        }

        // If no saved data or restore failed, add player normally
        if (challenge.addPlayer(player)) {
            playerChallengeMap[player.uniqueId] = challenge.id

            // Get the challenge world
            val world = Bukkit.getWorld(challenge.worldName) ?: loadWorld(challenge.worldName)
            if (world != null) {
                challenge.setupPlayerForChallenge(player, world)

                // Initialize player's world border if the feature is enabled
                if (challenge.settings.levelWorldBorder) {
                    plugin.experienceBorderListener.initializeWorldBordersForPlayers(challenge)
                }

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

            // Note: We no longer teleport or set gamemode here
            // The LobbyManager will handle that instead

            return true
        }

        return false
    }

    /**
     * Removes a player from the challenge map without affecting the player object.
     * Used when a player disconnects to ensure they start fresh in the lobby next time.
     */
    fun removePlayerFromChallenge(playerId: UUID) {
        playerChallengeMap.remove(playerId)
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

    /**
     * Exposes the WorldPreparationManager for commands to use
     */
    fun getWorldPreparationManager(): WorldPreparationManager {
        return worldPreparationManager
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
