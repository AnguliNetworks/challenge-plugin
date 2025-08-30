package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChallengeMenuManager(private val plugin: ChallengePluginPlugin) : Listener {

    private val playerMenus = ConcurrentHashMap<UUID, Inventory>()
    private val challengesPerInventory = ConcurrentHashMap<Inventory, List<ChallengeMenuData>>()
    private val filterToggleKey = NamespacedKey(plugin, "filter_show_all")
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }
    
    fun cleanup() {
        HandlerList.unregisterAll(this)
        playerMenus.clear()
        challengesPerInventory.clear()
    }
    
    fun openMainMenu(player: Player, showAll: Boolean = false) {
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
        inventory.setItem(0, createItem)
        
        // Filter toggle button (slot 4)
        val filterItem = createFilterToggleItem(player, showAll)
        inventory.setItem(4, filterItem)
        
        // Create Leave Challenge button
        val leaveItem = ItemStack(Material.BARRIER)
        val leaveMeta = leaveItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)
        leaveMeta.setDisplayName(plugin.languageManager.getMessage("challenge.menu.leave", player))
        leaveMeta.lore = listOf(plugin.languageManager.getMessage("challenge.menu.leave_lore", player))
        leaveItem.itemMeta = leaveMeta
        inventory.setItem(8, leaveItem)
        
        // Get challenges using efficient database query
        val challenges = plugin.challengeManager.getChallengesForMenu(player.uniqueId, showAll)
        val playerCurrentChallenge = plugin.challengeManager.getPlayerChallenge(player)
        
        // Store the challenges for this inventory so we can retrieve them in the click handler
        challengesPerInventory[inventory] = challenges
        
        // Add challenges to inventory
        if (challenges.isNotEmpty()) {
            challenges.forEachIndexed { index, challengeData ->
                // Start at slot 18 (third row) and fill rows from there
                val slot = 18 + index
                
                // Skip if we've reached the end of the inventory
                if (slot >= inventory.size) return@forEachIndexed
                
                val isCurrentChallenge = playerCurrentChallenge?.id == challengeData.id
                val challengeItem = createChallengeItemFromData(challengeData, player, isCurrentChallenge)
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
    
    private fun createFilterToggleItem(player: Player, showAll: Boolean): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD)
        
        // Store filter state in persistent data
        meta.persistentDataContainer.set(filterToggleKey, PersistentDataType.BYTE, if (showAll) 1 else 0)
        
        if (showAll) {
            meta.setDisplayName(plugin.languageManager.getMessage("challenge.menu.filter_all", player))
            meta.lore = listOf(plugin.languageManager.getMessage("challenge.menu.filter_all_lore", player))
        } else {
            meta.setDisplayName(plugin.languageManager.getMessage("challenge.menu.filter_your", player))
            meta.lore = listOf(plugin.languageManager.getMessage("challenge.menu.filter_your_lore", player))
            
            // Set to player's head
            if (meta is org.bukkit.inventory.meta.SkullMeta) {
                meta.owningPlayer = player
            }
        }
        
        item.itemMeta = meta
        return item
    }
    
    private fun createChallengeItemFromData(challengeData: ChallengeMenuData, player: Player, isCurrentChallenge: Boolean): ItemStack {
        val material = if (isCurrentChallenge) Material.ENCHANTED_BOOK else Material.BOOK
        
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)
        
        meta.setDisplayName(
            if (isCurrentChallenge) 
                plugin.languageManager.getMessage("challenge.menu.current_challenge", player, "name" to challengeData.name)
            else 
                plugin.languageManager.getMessage("challenge.menu.challenge", player, "name" to challengeData.name)
        )
        
        val loreList = mutableListOf<String>()
        
        // Challenge ID
        loreList.add(plugin.languageManager.getMessage("challenge.menu.id", player, "id" to challengeData.id.toString()))
        
        // Players
        loreList.add(plugin.languageManager.getMessage("challenge.menu.players", player, "count" to challengeData.playerCount.toString()))
        
        // Player status for this challenge
        val playerStatusMessage = when(challengeData.playerStatus) {
            "active" -> plugin.languageManager.getMessage("player_status.active", player)
            "failed" -> plugin.languageManager.getMessage("player_status.failed", player)
            "completed" -> plugin.languageManager.getMessage("player_status.completed", player)
            else -> plugin.languageManager.getMessage("player_status.not_joined", player)
        }
        loreList.add(plugin.languageManager.getMessage("challenge.menu.your_status", player, "status" to playerStatusMessage))
        
        // Duration (if started)
        if (challengeData.startedAt != null) {
            loreList.add(plugin.languageManager.getMessage("challenge.menu.duration", player, "time" to challengeData.getFormattedDuration()))
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
            0 -> {
                player.closeInventory()
                player.performCommand("create ") // Open create name prompt
            }
            
            // Filter Toggle button
            4 -> {
                val filterItem = event.currentItem ?: return
                val currentShowAll = filterItem.itemMeta?.persistentDataContainer?.get(filterToggleKey, PersistentDataType.BYTE) == 1.toByte()
                
                // Toggle filter and refresh menu
                player.closeInventory()
                openMainMenu(player, !currentShowAll)
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
                    val challengeData = challenges[index]
                    val currentChallenge = plugin.challengeManager.getPlayerChallenge(player)
                    
                    if (currentChallenge?.id == challengeData.id) {
                        // Player clicked their current challenge, leave it
                        plugin.playerDataManager.savePlayerData(player, challengeData.id)
                        
                        if (plugin.challengeManager.leaveChallenge(player)) {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.left", player, "name" to challengeData.name))
                            
                            // Refresh the menu
                            player.closeInventory()
                            openMainMenu(player)
                        } else {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.leave_failed", player))
                        }
                    } else {
                        // Player clicked a different challenge, join it
                        if (challengeData.status != ChallengeStatus.ACTIVE) {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.already_completed", player))
                            return
                        }
                        
                        // Get full challenge object from memory to join
                        val fullChallenge = plugin.challengeManager.getChallenge(challengeData.id)
                        if (fullChallenge != null && plugin.challengeManager.joinChallenge(player, fullChallenge)) {
                            player.sendMessage(plugin.languageManager.getMessage("challenge.joined", player, "name" to challengeData.name))
                            
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