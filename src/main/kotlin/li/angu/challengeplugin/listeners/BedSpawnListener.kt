package li.angu.challengeplugin.listeners

import io.papermc.paper.event.player.PlayerBedFailEnterEvent
import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerBedEnterEvent

/**
 * Listens for players entering beds to save their spawn location for challenge respawning.
 */
class BedSpawnListener(private val plugin: ChallengePluginPlugin) : Listener {


    fun saveBed(player: Player, bed: Block) {
        // Check if player is in an active challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return

        // Get the bed location
        val bedLocation = bed.location

        // Save the bed spawn location for this player in this challenge
        plugin.bedSpawnManager.saveBedSpawn(player.uniqueId, challenge.id, bedLocation)
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerBedFailedEnter(event: PlayerBedFailEnterEvent) {
        // Track bed entries that set spawn point or attempt to
        // NOT_POSSIBLE_NOW: Daytime but spawn point is still set
        // NOT_SAFE: Monsters nearby but spawn point is still set
        val validResults = setOf(
            PlayerBedFailEnterEvent.FailReason.NOT_POSSIBLE_NOW,
            PlayerBedFailEnterEvent.FailReason.NOT_SAFE,
        )

        if (event.failReason !in validResults) {
            return
        }

        saveBed(event.player, event.bed)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerBedEnter(event: PlayerBedEnterEvent) {
        // Track bed entries that set spawn point or attempt to
        // OK: Player successfully entered bed and set spawn
        // NOT_POSSIBLE_NOW: Daytime but spawn point is still set
        // NOT_SAFE: Monsters nearby but spawn point is still set
        val validResults = setOf(
            PlayerBedEnterEvent.BedEnterResult.OK,
            PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW,
            PlayerBedEnterEvent.BedEnterResult.NOT_SAFE,
        )

        if (event.bedEnterResult !in validResults) {
            return
        }

        saveBed(event.player, event.bed)
    }
}
