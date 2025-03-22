package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
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
                val challenge = Challenge(
                    id = id,
                    name = config.getString("name", "Unknown") ?: "Unknown",
                    worldName = config.getString("worldName", "world_${id}") ?: "world_${id}",
                    status = ChallengeStatus.valueOf(config.getString("status", "ACTIVE") ?: "ACTIVE"),
                    players = config.getStringList("players")
                        .map { UUID.fromString(it) }
                        .toMutableSet()
                )

                // Load timestamps if they exist
                if (config.contains("startedAt")) {
                    challenge.startedAt = Instant.ofEpochSecond(config.getLong("startedAt"))
                }

                if (config.contains("completedAt")) {
                    challenge.completedAt = Instant.ofEpochSecond(config.getLong("completedAt"))
                }

                activeChallenges[id] = challenge

                // Update player map
                challenge.players.forEach { playerId ->
                    playerChallengeMap[playerId] = id
                }

                // Load world if not loaded
                loadWorld(challenge.worldName)
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

            config.save(file)
        }
    }

    fun createChallenge(name: String): Challenge {
        val id = UUID.randomUUID()
        val worldName = "challenge_${id.toString().substring(0, 8)}"

        // Create new world
        val world = createHardcoreWorld(worldName)

        val challenge = Challenge(id, name, worldName)
        activeChallenges[id] = challenge

        return challenge
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

    fun joinChallenge(player: Player, challenge: Challenge): Boolean {
        // Check if player is already in a challenge
        val currentChallenge = getPlayerChallenge(player)
        if (currentChallenge != null) {
            if (currentChallenge.id == challenge.id) {
                return false // Already in this challenge
            }
            leaveChallenge(player)
        }

        // Add player to challenge
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

        if (challenge.removePlayer(player)) {
            playerChallengeMap.remove(player.uniqueId)

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

    private fun createHardcoreWorld(worldName: String): World? {
        val creator = WorldCreator(worldName)
        val world = creator.createWorld() ?: return null

        // Set hardcore game rules
        world.setGameRule(GameRule.NATURAL_REGENERATION, false)
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        world.difficulty = org.bukkit.Difficulty.HARD

        plugin.logger.info("Created new hardcore world: $worldName")
        return world
    }

    private fun loadWorld(worldName: String): World? {
        return try {
            if (Bukkit.getWorld(worldName) == null) {
                Bukkit.createWorld(WorldCreator(worldName))
            } else {
                Bukkit.getWorld(worldName)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load world $worldName: ${e.message}")
            null
        }
    }
}
