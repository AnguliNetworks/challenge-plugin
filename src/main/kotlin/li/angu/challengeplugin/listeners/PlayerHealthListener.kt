package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent

/**
 * Listens for player health changes and synchronizes them across players
 * in the same challenge when the sync hearts setting is enabled.
 */
class PlayerHealthListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        // Check if health sync is enabled for this challenge
        if (!challenge.settings.syncHearts) return
        
        // Synchronize the health change after the damage is applied
        // Using a delayed task to ensure the damage is applied first
        plugin.server.scheduler.runTask(plugin, Runnable {
            // Only proceed if the player is still online and in the challenge
            if (!player.isOnline || !challenge.isPlayerInChallenge(player)) return@Runnable
            
            // Get the player's new health after damage
            val newHealth = player.health
            
            // Apply to all other players in the challenge
            challenge.players.forEach { playerId ->
                if (playerId != player.uniqueId) {
                    val otherPlayer = plugin.server.getPlayer(playerId)
                    if (otherPlayer != null && otherPlayer.isOnline && otherPlayer.health != newHealth) {
                        otherPlayer.health = newHealth
                    }
                }
            }
        })
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerRegainHealth(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        // Check if health sync is enabled for this challenge
        if (!challenge.settings.syncHearts) return
        
        // Calculate the player's new health after regeneration
        // Health can't exceed max health
        val newHealth = minOf(player.health + event.amount, player.getMaxHealth())
        
        // Synchronize the health change after the health regeneration is applied
        plugin.server.scheduler.runTask(plugin, Runnable {
            // Only proceed if the player is still online and in the challenge
            if (!player.isOnline || !challenge.isPlayerInChallenge(player)) return@Runnable
            
            // Apply to all other players in the challenge
            challenge.players.forEach { playerId ->
                if (playerId != player.uniqueId) {
                    val otherPlayer = plugin.server.getPlayer(playerId)
                    if (otherPlayer != null && otherPlayer.isOnline && otherPlayer.health != newHealth) {
                        otherPlayer.health = newHealth
                    }
                }
            }
        })
    }
}