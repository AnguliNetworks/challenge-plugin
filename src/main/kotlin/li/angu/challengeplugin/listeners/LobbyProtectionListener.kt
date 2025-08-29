package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.EntityPickupItemEvent

/**
 * Protects the lobby world from player modifications and interactions.
 * Prevents players from breaking, placing, or interacting with blocks in the lobby.
 */
class LobbyProtectionListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (isInLobby(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (isInLobby(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        
        // Allow interaction with the menu item
        if (plugin.lobbyManager.isMenuItem(event.item)) {
            return
        }
        
        if (isInLobby(player)) {
            // Cancel interactions with blocks (except air)
            if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.LEFT_CLICK_BLOCK) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Player && isInLobby(entity)) {
            // Prevent all damage in lobby
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val damaged = event.entity
        
        // Prevent players from damaging anything in lobby
        if (damager is Player && isInLobby(damager)) {
            event.isCancelled = true
        }
        
        // Prevent anything from damaging players in lobby
        if (damaged is Player && isInLobby(damaged)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (isInLobby(player)) {
            // Prevent dropping items in lobby
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val entity = event.entity
        if (entity is Player && isInLobby(entity)) {
            // Prevent picking up items in lobby (except our menu item)
            val item = event.item.itemStack
            if (!plugin.lobbyManager.isMenuItem(item)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val entity = event.entity
        if (entity is Player && isInLobby(entity)) {
            // Prevent hunger in lobby
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (isInLobby(player)) {
            // Allow all inventory interactions in lobby - players should be able to organize their items
            // Only prevent interactions with external inventories (chests, etc.)
            if (event.clickedInventory != player.inventory) {
                event.isCancelled = true
            }
        }
    }

    /**
     * Check if a player is currently in the lobby world
     */
    private fun isInLobby(player: Player): Boolean {
        return plugin.lobbyManager.isLobbyWorld(player.world.name)
    }
}