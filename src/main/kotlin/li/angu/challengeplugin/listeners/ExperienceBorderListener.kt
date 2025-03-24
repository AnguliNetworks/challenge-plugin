package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpLevelChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerChangedWorldEvent

/**
 * This listener manages the world border adjustment based on player experience level
 * when the Level WorldBorder setting is enabled.
 */
class ExperienceBorderListener(private val plugin: ChallengePluginPlugin) : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * When a player's level changes, adjust the world border
     */
    @EventHandler
    fun onPlayerLevelUp(event: PlayerExpLevelChangeEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        // Check if the level world border setting is enabled for this challenge
        if (!challenge.settings.levelWorldBorder) return
        
        // Only apply when level increases (not when it decreases)
        if (event.newLevel <= event.oldLevel) return
        
        // Get the player's current world
        val world = player.world
        
        // Calculate new border size: starting with 3x3 (size = 3.0)
        // and increasing by 1 in each direction per level (so +2 per level)
        val newSize = 3.0 + (event.newLevel * 2.0)
        
        // Set the border with some transition time (5 seconds)
        world.worldBorder.size = newSize
        
        // Send a message to players in the challenge
        val message = plugin.languageManager.getMessage(
            "challenge.border_expanded", 
            player, 
            "level" to event.newLevel.toString(),
            "size" to newSize.toInt().toString()
        )
        
        challenge.players.forEach { playerId ->
            plugin.server.getPlayer(playerId)?.sendMessage(message)
        }
    }
    
    /**
     * When a player joins, make sure the border is set correctly based on highest level player
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        if (!challenge.settings.levelWorldBorder) return
        
        adjustWorldBorderForChallenge(challenge)
    }
    
    /**
     * When a player changes worlds, make sure the border is updated in the new world
     */
    @EventHandler
    fun onPlayerChangeWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        if (!challenge.settings.levelWorldBorder) return
        
        // Only care about challenge worlds
        if (event.player.world.name == challenge.worldName || 
            event.player.world.name == "${challenge.worldName}_nether" || 
            event.player.world.name == "${challenge.worldName}_the_end") {
            
            // Set the border in the new world based on player's level
            val world = event.player.world
            val highestLevel = getHighestPlayerLevel(challenge)
            val newSize = 3.0 + (highestLevel * 2.0)
            
            world.worldBorder.size = newSize
        }
    }
    
    /**
     * Find the highest level among all players in a challenge
     */
    private fun getHighestPlayerLevel(challenge: li.angu.challengeplugin.models.Challenge): Int {
        var highestLevel = 0
        
        challenge.players.forEach { playerId ->
            val player = plugin.server.getPlayer(playerId)
            if (player != null && player.level > highestLevel) {
                highestLevel = player.level
            }
        }
        
        return highestLevel
    }
    
    /**
     * Adjust world border size for all worlds in a challenge based on highest player level
     */
    private fun adjustWorldBorderForChallenge(challenge: li.angu.challengeplugin.models.Challenge) {
        if (!challenge.settings.levelWorldBorder) return
        
        val highestLevel = getHighestPlayerLevel(challenge)
        val newSize = 3.0 + (highestLevel * 2.0)
        
        // Adjust main world
        plugin.server.getWorld(challenge.worldName)?.let { world ->
            world.worldBorder.size = newSize
        }
        
        // Adjust nether
        challenge.getNetherWorld()?.let { world ->
            world.worldBorder.size = newSize
        }
        
        // Adjust end
        challenge.getEndWorld()?.let { world ->
            world.worldBorder.size = newSize
        }
    }
}