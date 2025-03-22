package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/**
 * Handles player connection events (join/quit) to manage player data persistence
 * for challenges across sessions.
 */
class PlayerConnectionListener(private val plugin: ChallengePluginPlugin) : Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerId = player.uniqueId
        
        // Check if the player was in any active challenges
        for (challenge in plugin.challengeManager.getActiveChallenges()) {
            // Check if player data exists for this challenge
            if (plugin.playerDataManager.hasPlayerData(playerId, challenge.id)) {
                // Try to restore player data and automatically join the challenge
                if (plugin.playerDataManager.restorePlayerData(player, challenge.id)) {
                    // Update the challenge state to include this player again
                    addPlayerToChallengeWithoutReset(player, challenge)
                    
                    // Send reconnection message
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            "challenge.reconnected", 
                            player, 
                            "name" to challenge.name
                        )
                    )
                    
                    // We found and restored their data, so stop looking for more challenges
                    // This prevents accidentally restoring data for multiple challenges
                    return
                }
            }
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        
        // Save player data if they're in an active challenge
        if (challenge != null && challenge.isPlayerInChallenge(player)) {
            // Save player data for the challenge
            plugin.playerDataManager.savePlayerData(player, challenge.id)
            
            // Remove the player from the challenge to pause timer if needed
            challenge.removePlayer(player)
            
            // But keep the player in the challenge map for reconnection
            // The player data is saved and we'll restore it when they reconnect
        }
    }
    
    /**
     * Adds a player to a challenge without resetting their inventory or location
     * Used when a player reconnects to a challenge they previously left
     */
    private fun addPlayerToChallengeWithoutReset(player: Player, challenge: Challenge): Boolean {
        // Only add the player to the data structure without setting up inventory/teleporting
        if (challenge.addPlayer(player)) {
            // Update player-challenge mapping
            plugin.challengeManager.addPlayerToChallenge(player.uniqueId, challenge.id)
            return true
        }
        return false
    }
}