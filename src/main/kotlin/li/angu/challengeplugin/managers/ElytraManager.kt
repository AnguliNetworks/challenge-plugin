package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.FireworkMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.FireworkEffect
import org.bukkit.Color
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.entity.FireworkExplodeEvent
import org.bukkit.entity.Firework

/**
 * Manages Elytra and firework rocket functionality for lobby players.
 * Provides infinite flight capabilities with Level 3 rockets that don't get consumed.
 */
class ElytraManager(private val plugin: ChallengePluginPlugin) : Listener {

    private val lobbyElytraKey = NamespacedKey(plugin, "lobby_elytra")
    private val lobbyRocketKey = NamespacedKey(plugin, "lobby_rocket")

    /**
     * Equip a player with Elytra and infinite rockets for lobby flight
     */
    fun equipLobbyFlightGear(player: Player) {
        if (!plugin.lobbyManager.isLobbyWorld(player.world.name)) {
            return
        }

        // Equip Elytra in chestplate slot
        val elytra = createLobbyElytra(player)
        player.inventory.chestplate = elytra

        // Add rocket to inventory (slot 1, next to menu item which is in slot 0)
        val rocket = createLobbyRocket(player)
        player.inventory.setItem(1, rocket)
    }

    /**
     * Remove lobby flight gear from a player
     */
    fun removeLobbyFlightGear(player: Player) {
        // Remove Elytra from chestplate slot
        val chestplate = player.inventory.chestplate
        if (chestplate != null && isLobbyElytra(chestplate)) {
            player.inventory.chestplate = null
        }

        // Remove rockets from inventory
        for (i in 0 until player.inventory.size) {
            val item = player.inventory.getItem(i)
            if (item != null && isLobbyRocket(item)) {
                player.inventory.setItem(i, null)
            }
        }
    }

    /**
     * Create a lobby-specific Elytra item
     */
    private fun createLobbyElytra(player: Player): ItemStack {
        val elytra = ItemStack(Material.ELYTRA)
        val meta = elytra.itemMeta ?: plugin.server.itemFactory.getItemMeta(Material.ELYTRA)

        meta.setDisplayName(plugin.languageManager.getMessage("lobby.elytra.name", player))
        
        val lore = mutableListOf<String>()
        lore.add(plugin.languageManager.getMessage("lobby.elytra.lore", player))
        meta.lore = lore

        // Add persistent data tag to identify this as our lobby elytra
        meta.persistentDataContainer.set(lobbyElytraKey, PersistentDataType.BYTE, 1)

        // Set unbreakable to prevent durability loss
        meta.isUnbreakable = true

        elytra.itemMeta = meta
        return elytra
    }

    /**
     * Create a lobby-specific firework rocket (Level 3)
     */
    private fun createLobbyRocket(player: Player): ItemStack {
        val rocket = ItemStack(Material.FIREWORK_ROCKET)
        val meta = rocket.itemMeta as FireworkMeta

        meta.setDisplayName(plugin.languageManager.getMessage("lobby.rocket.name", player))
        
        val lore = mutableListOf<String>()
        lore.add(plugin.languageManager.getMessage("lobby.rocket.lore", player))
        meta.lore = lore

        // Set flight duration to 3 (Level 3 rocket)
        meta.power = 3

        // Add a colorful effect to make it look special
        val effect = FireworkEffect.builder()
            .with(FireworkEffect.Type.STAR)
            .withColor(Color.AQUA, Color.WHITE)
            .withFade(Color.BLUE)
            .flicker(true)
            .build()
        meta.addEffect(effect)

        // Add persistent data tag to identify this as our lobby rocket
        meta.persistentDataContainer.set(lobbyRocketKey, PersistentDataType.BYTE, 1)

        rocket.itemMeta = meta
        return rocket
    }

    /**
     * Check if an item is our lobby Elytra
     */
    fun isLobbyElytra(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.ELYTRA) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(lobbyElytraKey, PersistentDataType.BYTE)
    }

    /**
     * Check if an item is our lobby rocket
     */
    fun isLobbyRocket(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.FIREWORK_ROCKET) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(lobbyRocketKey, PersistentDataType.BYTE)
    }

    /**
     * Refresh a player's rocket if they're in the lobby and don't have one
     */
    private fun refreshLobbyRocket(player: Player) {
        if (!plugin.lobbyManager.isLobbyWorld(player.world.name)) {
            return
        }

        // Check if player already has a lobby rocket
        for (item in player.inventory.contents) {
            if (isLobbyRocket(item)) {
                return // Player already has a rocket
            }
        }

        // Add a new rocket to slot 1
        val rocket = createLobbyRocket(player)
        player.inventory.setItem(1, rocket)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        // Handle rocket usage
        if (isLobbyRocket(item) && 
            (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            
            // Only allow in lobby world
            if (!plugin.lobbyManager.isLobbyWorld(player.world.name)) {
                event.isCancelled = true
                return
            }

            // Only allow if player is gliding, but don't consume the rocket
            if (player.isGliding) {
                // Manually apply the rocket boost effect
                val velocity = player.velocity
                val direction = player.location.direction
                val boost = direction.multiply(1.2) // Slightly reduced boost for lobby flight
                player.velocity = velocity.add(boost)
                
                // Spawn the visual firework effect at player's location
                val firework = player.world.spawn(player.location, Firework::class.java)
                val fireworkMeta = firework.fireworkMeta
                
                // Copy the same effect from our rocket
                val effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.STAR)
                    .withColor(Color.AQUA, Color.WHITE)
                    .withFade(Color.BLUE)
                    .flicker(true)
                    .build()
                fireworkMeta.addEffect(effect)
                fireworkMeta.power = 0 // Explode immediately for visual effect
                
                firework.fireworkMeta = fireworkMeta
                
                // Make the firework explode immediately for the visual effect
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    firework.detonate()
                }, 1L)
            }

            // Cancel the event to prevent item consumption
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityToggleGlide(event: EntityToggleGlideEvent) {
        val player = event.entity as? Player ?: return
        
        // Only handle in lobby world
        if (!plugin.lobbyManager.isLobbyWorld(player.world.name)) {
            return
        }

        // If player is starting to glide, make sure they have our special elytra
        if (event.isGliding) {
            val chestplate = player.inventory.chestplate
            if (chestplate == null || !isLobbyElytra(chestplate)) {
                // Re-equip lobby elytra
                val elytra = createLobbyElytra(player)
                player.inventory.chestplate = elytra
            }
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val item = event.itemDrop.itemStack

        // Prevent dropping lobby flight gear
        if (plugin.lobbyManager.isLobbyWorld(player.world.name)) {
            if (isLobbyElytra(item) || isLobbyRocket(item)) {
                event.isCancelled = true
            }
        }
    }
}