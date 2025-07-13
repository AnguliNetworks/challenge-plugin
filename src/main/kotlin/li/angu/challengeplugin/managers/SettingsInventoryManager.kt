package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import li.angu.challengeplugin.models.CycleSetting
import li.angu.challengeplugin.models.Setting
import li.angu.challengeplugin.models.StarterKit
import li.angu.challengeplugin.models.ToggleSetting
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SettingsInventoryManager(private val plugin: ChallengePluginPlugin) : Listener {

    private val pendingChallenges = ConcurrentHashMap<UUID, Challenge>()
    private val playerSettingsInventories = ConcurrentHashMap<UUID, UUID>()
    private val settingsInventories = ConcurrentHashMap<UUID, Inventory>()
    private val startedChallenges = ConcurrentHashMap<UUID, Boolean>()
    private val settingSlotMap = ConcurrentHashMap<Int, Setting<*>>()
    
    private val settings = mutableListOf<Setting<*>>()
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        registerDefaultSettings()
    }
    
    private fun registerDefaultSettings() {
        // Add Natural Regeneration toggle
        settings.add(ToggleSetting(
            "natural_regeneration",
            true,
            Material.GOLDEN_APPLE,
            Material.ROTTEN_FLESH,
            plugin
        ))
        
        // Add Sync Hearts toggle
        settings.add(ToggleSetting(
            "sync_hearts",
            false,
            Material.TOTEM_OF_UNDYING,
            Material.WITHER_SKELETON_SKULL,
            plugin
        ))
        
        // Add Block Randomizer toggle
        settings.add(ToggleSetting(
            "block_randomizer",
            false,
            Material.COMMAND_BLOCK,
            Material.FURNACE,
            plugin
        ))
        
        // Add Level World Border toggle
        settings.add(ToggleSetting(
            "level_world_border",
            false,
            Material.EXPERIENCE_BOTTLE,
            Material.BARRIER,
            plugin
        ))
        
        // Add Starter Kit selection
        settings.add(CycleSetting(
            "starter_kit",
            StarterKit.NONE,
            StarterKit.values(),
            mapOf(
                StarterKit.NONE to Material.BARRIER,
                StarterKit.BASIC to Material.STONE_PICKAXE,
                StarterKit.STONE to Material.LEATHER_CHESTPLATE,
                StarterKit.IRON to Material.IRON_CHESTPLATE,
                StarterKit.DIAMOND to Material.DIAMOND_CHESTPLATE
            ),
            plugin
        ))
    }
    
    fun cleanup() {
        HandlerList.unregisterAll(this)
        pendingChallenges.clear()
        playerSettingsInventories.clear()
        settingsInventories.clear()
        startedChallenges.clear()
        settingSlotMap.clear()
    }
    
    /**
     * Adds a new setting to the inventory manager.
     * 
     * @param setting The setting to add
     * @return True if the setting was added, false if a setting with the same ID already exists
     */
    fun addSetting(setting: Setting<*>): Boolean {
        if (settings.any { it.id == setting.id }) {
            return false
        }
        settings.add(setting)
        return true
    }
    
    /**
     * Adds a new toggle setting (boolean on/off switch).
     * 
     * @param id The unique identifier for this setting
     * @param defaultValue The default value for this setting
     * @param enabledMaterial The material to show when the setting is enabled
     * @param disabledMaterial The material to show when the setting is disabled
     * @return True if the setting was added, false if a setting with the same ID already exists
     */
    fun addToggleSetting(
        id: String,
        defaultValue: Boolean,
        enabledMaterial: Material,
        disabledMaterial: Material
    ): Boolean {
        val setting = ToggleSetting(id, defaultValue, enabledMaterial, disabledMaterial, plugin)
        return addSetting(setting)
    }
    
    /**
     * Adds a new cycle setting (rotates through options).
     * 
     * @param id The unique identifier for this setting
     * @param defaultValue The default value for this setting
     * @param values All possible values for this setting
     * @param materials Map of values to materials to display for each value
     * @return True if the setting was added, false if a setting with the same ID already exists
     */
    fun <T : Enum<T>> addCycleSetting(
        id: String,
        defaultValue: T,
        values: Array<T>,
        materials: Map<T, Material>
    ): Boolean {
        val setting = CycleSetting(id, defaultValue, values, materials, plugin)
        return addSetting(setting)
    }
    
    /**
     * Removes a setting by its ID.
     * 
     * @param id The ID of the setting to remove
     * @return True if a setting was removed, false if no setting with that ID was found
     */
    fun removeSetting(id: String): Boolean {
        val initialSize = settings.size
        settings.removeIf { it.id == id }
        return settings.size < initialSize
    }
    
    /**
     * Gets a setting by its ID.
     * 
     * @param id The ID of the setting to get
     * @return The setting, or null if no setting with that ID exists
     */
    fun getSetting(id: String): Setting<*>? {
        return settings.find { it.id == id }
    }
    
    /**
     * Gets all settings.
     * 
     * @return A list of all settings
     */
    fun getAllSettings(): List<Setting<*>> {
        return settings.toList()
    }

    fun openSettingsInventory(player: Player, challenge: Challenge) {
        val title = plugin.languageManager.getMessage("challenge.settings.title", player, "name" to challenge.name)
        
        // Calculate needed rows based on settings count (minimum 3 rows)
        val settingsCount = settings.size
        val startButtonNeeded = true // We need 1 slot for start button
        val neededSlots = settingsCount + if (startButtonNeeded) 1 else 0
        
        // Each row has 9 slots, first and last slot of each row are borders,
        // so 7 usable slots per row. First and last row are all borders.
        val usableSlotsPerRow = 7
        val neededUsableRows = Math.ceil(neededSlots / usableSlotsPerRow.toDouble()).toInt()
        val totalRows = neededUsableRows + 3 // +2 for top and bottom border rows, +1 for spacing row
        
        // Ensure at least 4 rows (top border, settings, spacing, bottom with start button)
        val rowCount = Math.max(4, totalRows)
        val inventorySize = rowCount * 9
        
        val inventory = Bukkit.createInventory(player, inventorySize, title)
        
        // Create blank glass pane for empty slots
        val blankPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val paneMeta = blankPane.itemMeta
        paneMeta?.setDisplayName(" ")  // Empty name but not null
        blankPane.itemMeta = paneMeta
        
        // Fill inventory with blank panes first
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blankPane.clone())
        }
        
        // Clear previous slot mappings
        settingSlotMap.clear()
        
        // Add settings items
        var slot = 10 // Start at first slot of second row
        for (setting in settings) {
            when (setting) {
                is ToggleSetting -> {
                    val value = when (setting.id) {
                        "natural_regeneration" -> challenge.settings.naturalRegeneration
                        "sync_hearts" -> challenge.settings.syncHearts
                        "block_randomizer" -> challenge.settings.blockRandomizer
                        "level_world_border" -> challenge.settings.levelWorldBorder
                        else -> setting.defaultValue
                    }
                    val item = setting.createItem(value, player)
                    inventory.setItem(slot, item)
                    settingSlotMap[slot] = setting
                }
                is CycleSetting<*> -> {
                    if (setting.id == "starter_kit") {
                        @Suppress("UNCHECKED_CAST")
                        val cycleSetting = setting as CycleSetting<StarterKit>
                        val item = cycleSetting.createItem(challenge.settings.starterKit, player)
                        inventory.setItem(slot, item)
                        settingSlotMap[slot] = setting
                    }
                }
            }
            
            // Move to next slot
            slot++
            
            // If we reached the end of the row, skip to the next row
            if (slot % 9 == 8) {
                slot += 2 // Skip to the first slot of the next row
            }
        }
        
        // Add Start Challenge button in the last row, center position (with one row gap)
        val lastRowStart = inventory.size - 9
        val startButtonPosition = lastRowStart + 4
        
        val startItem = ItemStack(Material.DIAMOND_SWORD)
        val startMeta = startItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.DIAMOND_SWORD)
        startMeta.setDisplayName(plugin.languageManager.getMessage("challenge.settings.start", player))
        startMeta.lore = listOf(plugin.languageManager.getMessage("challenge.settings.start_lore", player))
        startItem.itemMeta = startMeta
        inventory.setItem(startButtonPosition, startItem)

        // Store the relationship between player and challenge
        pendingChallenges[player.uniqueId] = challenge
        playerSettingsInventories[player.uniqueId] = challenge.id
        settingsInventories[player.uniqueId] = inventory

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val challengeId = playerSettingsInventories[player.uniqueId] ?: return
        
        // Get the tracked settings inventory for this player
        val settingsInventory = settingsInventories[player.uniqueId] ?: return
        
        // Check if click is in our settings inventory, not player's own inventory
        if (event.clickedInventory != settingsInventory) {
            return
        }

        event.isCancelled = true // Prevent moving items

        val challenge = pendingChallenges[player.uniqueId] ?: return
        
        // Check if the clicked slot is the start button (center of last row)
        val lastRowStart = settingsInventory.size - 9
        val startButtonSlot = lastRowStart + 4
        
        if (event.slot == startButtonSlot) {
            // Mark this challenge as started before closing inventory
            startedChallenges[player.uniqueId] = true
            
            // Send title to player indicating challenge creation has started
            player.sendTitle(
                plugin.languageManager.getMessage("challenge.creating.title", player),
                plugin.languageManager.getMessage("challenge.creating.subtitle", player),
                10, 40, 20
            )
            
            // Play a dramatic sound when starting challenge creation
            player.playSound(player.location, "minecraft:entity.experience_orb.pickup", 1.0f, 0.5f)
            
            player.closeInventory()
            startChallenge(player, challenge)
            return
        }
        
        // Check if the clicked slot is a setting
        val setting = settingSlotMap[event.slot] ?: return
        
        when (setting) {
            is ToggleSetting -> {
                when (setting.id) {
                    "natural_regeneration" -> {
                        challenge.settings.naturalRegeneration = 
                            setting.onClick(challenge.settings.naturalRegeneration) as Boolean
                        event.inventory.setItem(event.slot, 
                            setting.createItem(challenge.settings.naturalRegeneration, player))
                    }
                    "sync_hearts" -> {
                        challenge.settings.syncHearts = 
                            setting.onClick(challenge.settings.syncHearts) as Boolean
                        event.inventory.setItem(event.slot, 
                            setting.createItem(challenge.settings.syncHearts, player))
                    }
                    "block_randomizer" -> {
                        challenge.settings.blockRandomizer = 
                            setting.onClick(challenge.settings.blockRandomizer) as Boolean
                        event.inventory.setItem(event.slot, 
                            setting.createItem(challenge.settings.blockRandomizer, player))
                    }
                    "level_world_border" -> {
                        challenge.settings.levelWorldBorder = 
                            setting.onClick(challenge.settings.levelWorldBorder) as Boolean
                        event.inventory.setItem(event.slot, 
                            setting.createItem(challenge.settings.levelWorldBorder, player))
                    }
                }
            }
            is CycleSetting<*> -> {
                if (setting.id == "starter_kit") {
                    @Suppress("UNCHECKED_CAST")
                    val cycleSetting = setting as CycleSetting<StarterKit>
                    challenge.settings.starterKit = 
                        cycleSetting.onClick(challenge.settings.starterKit) as StarterKit
                    event.inventory.setItem(event.slot, 
                        cycleSetting.createItem(challenge.settings.starterKit, player))
                }
            }
        }
        
        // Play a click sound
        player.playSound(player.location, "minecraft:ui.button.click", 1.0f, 1.0f)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val challengeId = playerSettingsInventories[player.uniqueId] ?: return

        // Check if this challenge was started by clicking the sword button
        val wasStarted = startedChallenges.remove(player.uniqueId) ?: false

        // Clean up
        playerSettingsInventories.remove(player.uniqueId)
        settingsInventories.remove(player.uniqueId)

        // If the challenge wasn't started by clicking the sword button
        // and we have a pending challenge, then the player just closed the settings inventory
        if (!wasStarted) {
            val challenge = pendingChallenges[player.uniqueId]
            if (challenge != null) {
                pendingChallenges.remove(player.uniqueId)
                plugin.challengeManager.removeChallenge(challenge.id)
                player.sendMessage(plugin.languageManager.getMessage("challenge.create_cancelled", player))
            }
        }
    }

    private fun startChallenge(player: Player, challenge: Challenge) {
        // Remove this challenge from pending before inventory close handler is triggered
        pendingChallenges.remove(player.uniqueId)

        // Run world creation after a very short delay to ensure inventory close
        // handling is complete
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.challengeManager.finalizeChallenge(challenge)) {
                player.sendMessage(plugin.languageManager.getMessage("challenge.created", player, "name" to challenge.name))
                player.sendMessage(plugin.languageManager.getMessage("challenge.join_command", player, "id" to challenge.id.toString()))
            } else {
                plugin.challengeManager.removeChallenge(challenge.id)
                player.sendMessage(plugin.languageManager.getMessage("challenge.create_failed", player))
            }
        }, 1L) // 1 tick delay (1/20 of a second)
    }
}