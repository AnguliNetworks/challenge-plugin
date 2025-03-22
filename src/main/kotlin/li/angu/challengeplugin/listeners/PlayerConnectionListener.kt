package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID

/**
 * Handles player connection events (join/quit) to manage player data persistence
 * for challenges across sessions.
 */
class PlayerConnectionListener(private val plugin: ChallengePluginPlugin) : Listener {
    
    private val menuItemKey = NamespacedKey(plugin, "challenge_menu_item")
    
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
        
        // If player is not in a challenge and is in the main world, give them the menu item
        if (plugin.challengeManager.getPlayerChallenge(player) == null && isMainWorld(player.world.name)) {
            giveMenuItemIfNeeded(player)
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
    
    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from
        val toWorld = player.world
        
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        
        if (challenge == null && isMainWorld(toWorld.name)) {
            // Player entered main world and is not in a challenge
            giveMenuItemIfNeeded(player)
        } else if (!isMainWorld(toWorld.name)) {
            // Player left main world, remove menu item
            removeMenuItem(player)
        }
    }
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // Check if the item is our menu item
        if (isMenuItem(item) && (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            event.isCancelled = true
            
            // Open the menu
            plugin.challengeMenuManager.openMainMenu(player)
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
    
    private fun isMainWorld(worldName: String): Boolean {
        // Typically the main world is named "world"
        return worldName == "world" || worldName == plugin.server.worlds.first().name
    }
    
    private fun giveMenuItemIfNeeded(player: Player) {
        // Check if player already has the menu item
        if (player.inventory.contents.any { isMenuItem(it) }) {
            return
        }
        
        // Create menu item
        val menuItem = createMenuItem(player)
        
        // Give to player in the last hotbar slot
        player.inventory.setItem(8, menuItem)
    }
    
    private fun removeMenuItem(player: Player) {
        // Find and remove menu item
        player.inventory.contents.forEachIndexed { index, item ->
            if (isMenuItem(item)) {
                player.inventory.setItem(index, null)
            }
        }
    }
    
    private fun createMenuItem(player: Player): ItemStack {
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta ?: plugin.server.itemFactory.getItemMeta(Material.NETHER_STAR)
        
        meta.setDisplayName(plugin.languageManager.getMessage("challenge.menu_item.name", player))
        
        val lore = mutableListOf<String>()
        lore.add(plugin.languageManager.getMessage("challenge.menu_item.lore", player))
        meta.lore = lore
        
        // Add a persistent data tag to identify this as our item
        meta.persistentDataContainer.set(menuItemKey, PersistentDataType.BYTE, 1)
        
        item.itemMeta = meta
        return item
    }
    
    private fun isMenuItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.NETHER_STAR) return false
        
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(menuItemKey, PersistentDataType.BYTE)
    }
}