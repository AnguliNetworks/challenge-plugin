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

    @EventHandler
    fun onEnderDragonDeath(event: EntityDeathEvent) {
        val entity = event.entity

        // check if the entity is an EnderDragon
        if (entity !is EnderDragon) {
            return
        }

        val world = entity.world
        val worldName = world.name

        // Find the challenge associated with this world
        val challenge = plugin.challengeManager.getAllChallenges()
            .find { it.worldName == worldName } ?: return

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

        // In hardcore mode, store death location and set to spectator upon respawn
        deathLocations[player.uniqueId] = player.location
        player.sendMessage(plugin.languageManager.getMessage("challenge.died", player))
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val deathLocation = deathLocations[player.uniqueId] ?: return

        // Set respawn at death location
        event.respawnLocation = deathLocation

        // Set player to spectator mode
        player.gameMode = GameMode.SPECTATOR

        // Remove from death locations map after handling
        deathLocations.remove(player.uniqueId)
    }
}
