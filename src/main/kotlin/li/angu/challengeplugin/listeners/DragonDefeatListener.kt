package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.EnderDragon
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.World
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.Location
import java.util.UUID
import org.bukkit.GameMode
import org.bukkit.event.entity.EnderDragonChangePhaseEvent

class DragonDefeatListener(private val plugin: ChallengePluginPlugin) : Listener {

    private val deathLocations = HashMap<UUID, Location>()

    @EventHandler(ignoreCancelled = true)
    fun onEnderDragonDeath(event: EntityDeathEvent) {
        val entity = event.entity

        // check if the entity is an EnderDragon
        if (entity !is EnderDragon) {
            return
        }

        val world = entity.world
        val worldName = world.name

        // Find the challenge associated with this end world
        // The end world name format is "challenge_name_the_end"
        val baseWorldName = worldName.replace("_the_end", "")
        val challenge = plugin.challengeManager.getAllChallenges()
            .find { it.worldName == baseWorldName } ?: return

        plugin.challengeManager.completeChallenge(challenge.id)

        // Announce to all players in the challenge
        plugin.server.onlinePlayers.forEach { player ->
            if (!challenge.isPlayerInChallenge(player)) {
                return@forEach
            }

            player.sendMessage(plugin.languageManager.getMessage("challenge.dragon_defeated", player))
            player.sendTitle(
                plugin.languageManager.getMessage("challenge.dragon_defeated_title", player),
                plugin.languageManager.getMessage("challenge.dragon_defeated_subtitle", player),
                10, 70, 20
            )
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        // Find if player is in a challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return

        // Only apply hardcore death mechanics if challenge is in hardcore mode
        if (challenge.settings.difficulty == li.angu.challengeplugin.models.Difficulty.HARDCORE) {
            // In hardcore mode, store death location and set to spectator upon respawn
            deathLocations[player.uniqueId] = player.location
            player.sendMessage(plugin.languageManager.getMessage("challenge.died", player))
        }
        // For non-hardcore modes, let Minecraft handle respawning normally (bed/world spawn)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        // Check if player is in a challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        if (challenge == null) {
            // Not in a challenge, clean up if needed
            deathLocations.remove(player.uniqueId)
            return
        }

        // Get the challenge's overworld
        val challengeWorld = plugin.server.getWorld(challenge.worldName)
        if (challengeWorld == null) {
            plugin.logger.warning("Could not find challenge world ${challenge.worldName} for respawn")
            deathLocations.remove(player.uniqueId)
            return
        }

        // Handle HARDCORE mode
        if (challenge.settings.difficulty == li.angu.challengeplugin.models.Difficulty.HARDCORE) {
            val deathLocation = deathLocations[player.uniqueId]
            if (deathLocation != null) {
                // Hardcore mode: respawn at death location and set to spectator
                event.respawnLocation = deathLocation
                player.gameMode = GameMode.SPECTATOR
                deathLocations.remove(player.uniqueId)
            }
            return
        }

        // Handle NON-HARDCORE mode: respawn at bed or world spawn
        deathLocations.remove(player.uniqueId) // Clean up death location if stored

        // Try to get saved bed spawn location
        val bedSpawn = plugin.bedSpawnManager.getBedSpawn(player.uniqueId, challenge.id)

        if (bedSpawn != null) {
            // Check if bed is still valid (not destroyed)
            if (plugin.bedSpawnManager.isBedValid(bedSpawn)) {
                // Bed exists, respawn there
                event.respawnLocation = bedSpawn.add(0.5, 0.5, 0.5)
                plugin.logger.fine("Respawning ${player.name} at bed location in ${bedSpawn.world?.name}")
            } else {
                // Bed was destroyed, delete from database and use world spawn
                plugin.bedSpawnManager.deleteBedSpawn(player.uniqueId, challenge.id)
                event.respawnLocation = challengeWorld.spawnLocation
                plugin.logger.fine("Bed destroyed, respawning ${player.name} at world spawn")
            }
        } else {
            // No bed spawn saved, use world spawn
            event.respawnLocation = challengeWorld.spawnLocation
            plugin.logger.fine("No bed spawn, respawning ${player.name} at world spawn")
        }
    }
}
