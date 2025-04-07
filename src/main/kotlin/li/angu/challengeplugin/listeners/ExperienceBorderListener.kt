package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * This listener manages the world border adjustment based on player experience level
 * when the Level WorldBorder setting is enabled.
 * 
 * This implementation simulates per-player borders by repeatedly updating the world border
 * for each player, making it appear as if they have individual borders.
 */
class ExperienceBorderListener(private val plugin: ChallengePluginPlugin) : Listener {
    
    // Store the border size for each challenge
    private val challengeBorderSizes = ConcurrentHashMap<UUID, Double>()
    
    // Border update task
    private var borderUpdateTask: BukkitTask? = null
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        
        // Start a repeating task to update borders for all players
        // We only need to update occasionally since we're storing per-player borders
        borderUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateAllPlayerBorders()
        }, 10L, 20L) // Update every 20 ticks (1 second)
    }
    
    /**
     * Update borders for all online players in challenges
     */
    private fun updateAllPlayerBorders() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return@forEach
            
            if (!challenge.settings.levelWorldBorder) return@forEach
            
            val borderSize = challengeBorderSizes[challenge.id] ?: calculateBorderSize(challenge)
            updatePlayerBorder(player, borderSize, player.world.spawnLocation)
        }
    }
    
    // Store a world border for each world to avoid recreating it
    private val worldBorders = mutableMapOf<String, WorldBorder>()
    
    /**
     * Update a single player's border
     */
    private fun updatePlayerBorder(player: Player, size: Double, center: Location) {
        try {
            val world = player.world
            
            // Create a custom WorldBorder object for each world if it doesn't exist
            val worldBorder = worldBorders.computeIfAbsent(world.name) { 
                // Create a new WorldBorder directly
                val border = Bukkit.createWorldBorder()
                // Set this to be very far away and very large by default
                border.center = Location(world, 0.0, 0.0, 0.0)
                border.size = 60000000.0 // Nearly unlimited
                border
            }
            
            // Only update if necessary
            if (worldBorder.size != size || worldBorder.center != center) {
                worldBorder.size = size
                worldBorder.center = center
            }
            
            // Send the border to the player without changing the world's actual border
            player.worldBorder = worldBorder
        } catch (e: Exception) {
            // Log any errors but don't crash the plugin
            plugin.logger.warning("Error updating border for player ${player.name}: ${e.message}")
        }
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
        
        // Calculate new size (2 blocks per level)
        val newSize = 3.0 + (event.newLevel * 2.0)
        
        // Update the size in our map
        challengeBorderSizes[challenge.id] = newSize
        
        // Update borders for all players in this challenge immediately
        challenge.players.forEach { playerId ->
            plugin.server.getPlayer(playerId)?.let { challengePlayer ->
                updatePlayerBorder(challengePlayer, newSize, challengePlayer.world.spawnLocation)
            }
        }
        
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
     * When a player joins, initialize their border
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        if (!challenge.settings.levelWorldBorder) return
        
        // Immediately update the border for the player
        val borderSize = challengeBorderSizes[challenge.id] ?: calculateBorderSize(challenge)
        updatePlayerBorder(player, borderSize, player.world.spawnLocation)
    }
    
    /**
     * When a player changes worlds, update their border center
     */
    @EventHandler
    fun onPlayerChangeWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return
        
        if (!challenge.settings.levelWorldBorder) return
        
        // Immediately update the border for the player in the new world
        val borderSize = challengeBorderSizes[challenge.id] ?: calculateBorderSize(challenge)
        updatePlayerBorder(player, borderSize, player.world.spawnLocation)
    }
    
    /**
     * Calculate the border size based on the highest player level in the challenge
     */
    private fun calculateBorderSize(challenge: li.angu.challengeplugin.models.Challenge): Double {
        var highestLevel = 0
        
        // Find the highest level among online players in this challenge
        challenge.players.forEach { playerId ->
            plugin.server.getPlayer(playerId)?.let { player ->
                if (player.level > highestLevel) {
                    highestLevel = player.level
                }
            }
        }
        
        // Calculate border size (3 blocks base + 2 per level)
        return 3.0 + (highestLevel * 2.0)
    }
    
    /**
     * Initialize world borders for players in a challenge
     * This method should be called when a challenge is created or reset
     */
    fun initializeWorldBordersForPlayers(challenge: li.angu.challengeplugin.models.Challenge) {
        if (!challenge.settings.levelWorldBorder) return
        
        // Calculate the border size for this challenge
        val borderSize = calculateBorderSize(challenge)
        
        // Store it for this challenge
        challengeBorderSizes[challenge.id] = borderSize
        
        // The borders will be updated by the repeating task
    }
    
    /**
     * Clean up tasks when plugin is disabled
     */
    fun cleanup() {
        borderUpdateTask?.cancel()
        borderUpdateTask = null
    }
}