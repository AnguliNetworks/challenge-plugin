package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChallengeMenuManager(private val plugin: ChallengePluginPlugin) : Listener {

    private val playerMenus = ConcurrentHashMap<UUID, Inventory>()
    private val challengesPerInventory = ConcurrentHashMap<Inventory, List<Challenge>>()
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }
    
    fun cleanup() {
        HandlerList.unregisterAll(this)
        playerMenus.clear()
        challengesPerInventory.clear()
    }
    
    fun openMainMenu(player: Player) {
        val inventory = Bukkit.createInventory(
            player,
            54, // 6 rows
            plugin.languageManager.getMessage("challenge.menu.title", player)
        )
        
        // Create blank glass pane for empty slots
        val blankPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val paneMeta = blankPane.itemMeta
        paneMeta?.setDisplayName(" ")  // Empty name but not null
        blankPane.itemMeta = paneMeta
        
        // Fill inventory with blank panes first
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blankPane.clone())
        }
        
        // Create Challenge button
        val createItem = ItemStack(Material.NETHER_STAR)
        val createMeta = createItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.NETHER_STAR)
        createMeta.setDisplayName(plugin.languageManager.getMessage("challenge.menu.create", player))
        createMeta.lore = listOf(plugin.languageManager.getMessage("challenge.menu.create_lore", player))
        createItem.itemMeta = createMeta
        inventory.setItem(4, createItem)
        
        // Create Leave Challenge button
        val leaveItem = ItemStack(Material.BARRIER)
        val leaveMeta = leaveItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)
        leaveMeta.setDisplayName(plugin.languageManager.getMessage("challenge.menu.leave", player))
        leaveMeta.lore = listOf(plugin.languageManager.getMessage("challenge.menu.leave_lore", player))
        leaveItem.itemMeta = leaveMeta
        inventory.setItem(8, leaveItem)
        
        // Get all active challenges
        val challenges = plugin.challengeManager.getActiveChallenges()
        val playerCurrentChallenge = plugin.challengeManager.getPlayerChallenge(player)
        
        // Store the challenges for this inventory so we can retrieve them in the click handler
        challengesPerInventory[inventory] = challenges
        
        // Add challenges to inventory
        if (challenges.isNotEmpty()) {
            challenges.forEachIndexed { index, challenge ->
                // Start at slot 18 (third row) and fill rows from there
                val slot = 18 + index
                
                // Skip if we've reached the end of the inventory
                if (slot >= inventory.size) return@forEachIndexed
                
                val isCurrentChallenge = playerCurrentChallenge?.id == challenge.id
                val challengeItem = createChallengeItem(challenge, player, isCurrentChallenge)
                inventory.setItem(slot, challengeItem)
            }
        } else {
            // "No challenges" message item
            val noItem = ItemStack(Material.PAPER)
            val noMeta = noItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PAPER)
            noMeta.setDisplayName(plugin.languageManager.getMessage("challenge.no_challenges", player))
            noItem.itemMeta = noMeta
            inventory.setItem(31, noItem)
        }
        
        // Store player's menu
        playerMenus[player.uniqueId] = inventory
        
        // Open inventory for player
        player.openInventory(inventory)
    }
    
    private fun createChallengeItem(challenge: Challenge, player: Player, isCurrentChallenge: Boolean): ItemStack {
        // Choose material based on whether this is the player's current challenge
        val material = when {
            isCurrentChallenge -> Material.ENCHANTED_BOOK
            else -> Material.BOOK
        }
        
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)
        
        meta.setDisplayName(
            if (isCurrentChallenge) 
                plugin.languageManager.getMessage("challenge.menu.current_challenge", player, "name" to challenge.name)
            else 
                plugin.languageManager.getMessage("challenge.menu.challenge", player, "name" to challenge.name)
        )
        
        val loreList = mutableListOf<String>()
        
        // Challenge ID
        loreList.add(plugin.languageManager.getMessage("challenge.menu.id", player, "id" to challenge.id.toString()))
        
        // Challenge status
        val statusKey = when(challenge.status) {
            ChallengeStatus.ACTIVE -> "status.active"
            ChallengeStatus.COMPLETED -> "status.completed"
            ChallengeStatus.FAILED -> "status.failed"
        }
        loreList.add(plugin.languageManager.getMessage("challenge.menu.status", player, 
            "status" to plugin.languageManager.getMessage(statusKey, player)))
        
        // Players
        loreList.add(plugin.languageManager.getMessage("challenge.menu.players", player, "count" to challenge.players.size.toString()))
        
        // Duration (if started)
        if (challenge.startedAt != null) {
            loreList.add(plugin.languageManager.getMessage("challenge.menu.duration", player, "time" to challenge.getFormattedDuration()))
        }
        
        // Action text
        if (isCurrentChallenge) {
            loreList.add(" ")
            loreList.add(plugin.languageManager.getMessage("challenge.menu.click_to_leave", player))
        } else {
            loreList.add(" ")
            loreList.add(plugin.languageManager.getMessage("challenge.menu.click_to_join", player))
        }
        
        meta.lore = loreList
        item.itemMeta = meta
        return item
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = playerMenus[player.uniqueId] ?: return
        
        // Check if click is in our menu inventory, not player's own inventory
        if (event.clickedInventory != inventory) {
            return
        }
        
        event.isCancelled = true // Prevent moving items
        
        val challenges = challengesPerInventory[inventory] ?: emptyList()
        
        when (event.slot) {
            // Create Challenge button
            4 -> {
                player.closeInventory()
                player.performCommand("create ") // Open create name prompt
            }
            
            // Leave Challenge button
            8 -> {
                val currentChallenge = plugin.challengeManager.getPlayerChallenge(player)
                if (currentChallenge != null) {
                    // Save player data before leaving
                    plugin.playerDataManager.savePlayerData(player, currentChallenge.id)
                    
                    if (plugin.challengeManager.leaveChallenge(player)) {
                        player.sendMessage(plugin.languageManager.getMessage("challenge.left", player, "name" to currentChallenge.name))
                        
                        // Refresh the menu
                        player.closeInventory()
                        openMainMenu(player)
                    } else {
                        player.sendMessage(plugin.languageManager.getMessage("challenge.leave_failed", player))
                    }
                } else {
                    player.sendMessage(plugin.languageManager.getMessage("challenge.not_in", player))
                }
            }
            
            // Challenge items (starting at slot 18)
            else -> {
                // Calculate index in the challenges list
                val index = event.slot - 18
                if (index >= 0 && index < challenges.size) {
                    val challenge = challenges[index]
                    val currentChallenge = plugin.challengeManager.getPlayerChallenge(player)
                    
                    if (currentChallenge?.id == challenge.id) {
                        // Player clicked their current challenge, leave it
                        plugin.playerDataManager.savePlayerData(player, challenge.id)
                        
                        if (plugin.challengeManager.leaveChallenge(player)) {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.left", player, "name" to challenge.name))
                            
                            // Refresh the menu
                            player.closeInventory()
                            openMainMenu(player)
                        } else {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.leave_failed", player))
                        }
                    } else {
                        // Player clicked a different challenge, join it
                        if (challenge.status != ChallengeStatus.ACTIVE) {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.already_completed", player))
                            return
                        }
                        
                        if (plugin.challengeManager.joinChallenge(player, challenge)) {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.joined", player, "name" to challenge.name))
                            
                            // Close the menu
                            player.closeInventory()
                        } else {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.join_failed", player))
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val inventory = playerMenus[player.uniqueId] ?: return
        
        // Clean up resources
        playerMenus.remove(player.uniqueId)
        challengesPerInventory.remove(inventory)
    }
}