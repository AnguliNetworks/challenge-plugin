package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
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
    fun onPlayerLevelUp(event: PlayerLevelChangeEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        // Check if the level world border setting is enabled for this challenge
        if (!challenge.settings.levelWorldBorder) return
        
        // Only apply when level increases (not when it decreases)
        if (event.newLevel <= event.oldLevel) return
        
        // Get the player's current world
        val world = player.world
        
        // Get current border size and add 2
        val currentSize = world.worldBorder.size
        val newSize = currentSize + 2.0
        
        // Set the border
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
     * When a player joins, don't modify the border
     * Border only expands when players level up
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // No longer adjusting border on join - only increases on level up
    }
    
    /**
     * When a player changes worlds, initialize the border if needed
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
            
            // Initialize the border in the new world if it's too small
            val world = event.player.world
            if (world.worldBorder.size < 3.0) {
                world.worldBorder.size = 3.0 // Initialize with minimum size
            }
        }
    }
    
    /**
     * Initialize world border size for all worlds in a challenge
     * This method can be called when a challenge is created to set initial border
     */
    private fun initializeWorldBordersForChallenge(challenge: li.angu.challengeplugin.models.Challenge) {
        if (!challenge.settings.levelWorldBorder) return
        
        val initialSize = 3.0 // Starting with 3x3 border
        
        // Initialize main world
        plugin.server.getWorld(challenge.worldName)?.let { world ->
            world.worldBorder.size = initialSize
        }
        
        // Initialize nether
        challenge.getNetherWorld()?.let { world ->
            world.worldBorder.size = initialSize
        }
        
        // Initialize end
        challenge.getEndWorld()?.let { world ->
            world.worldBorder.size = initialSize
        }
    }
}