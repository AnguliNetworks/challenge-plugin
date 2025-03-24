package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
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

class ChallengeSettingsManager(private val plugin: ChallengePluginPlugin) : Listener {

    private val pendingChallenges = ConcurrentHashMap<UUID, Challenge>()
    private val playerSettingsInventories = ConcurrentHashMap<UUID, UUID>()
    private val settingsInventories = ConcurrentHashMap<UUID, Inventory>()
    private val startedChallenges = ConcurrentHashMap<UUID, Boolean>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun cleanup() {
        HandlerList.unregisterAll(this)
        pendingChallenges.clear()
        playerSettingsInventories.clear()
        settingsInventories.clear()
        startedChallenges.clear()
    }

    fun openSettingsInventory(player: Player, challenge: Challenge) {
        val inventory = Bukkit.createInventory(
            player,
            27,
            plugin.languageManager.getMessage("challenge.settings.title", player, "name" to challenge.name)
        )
        
        // Create blank glass pane for empty slots
        val blankPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val paneMeta = blankPane.itemMeta
        paneMeta?.setDisplayName(" ")  // Empty name but not null
        blankPane.itemMeta = paneMeta
        
        // Fill inventory with blank panes first
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blankPane.clone())
        }

        // Add Natural Regeneration toggle
        val naturalRegenItem = createToggleItem(
            if (challenge.settings.naturalRegeneration) Material.GOLDEN_APPLE else Material.ROTTEN_FLESH,
            "natural_regeneration",
            challenge.settings.naturalRegeneration,
            player
        )
        inventory.setItem(10, naturalRegenItem)
        
        // Add Sync Hearts toggle
        val syncHeartsItem = createToggleItem(
            if (challenge.settings.syncHearts) Material.TOTEM_OF_UNDYING else Material.WITHER_SKELETON_SKULL,
            "sync_hearts",
            challenge.settings.syncHearts,
            player
        )
        inventory.setItem(11, syncHeartsItem)
        
        // Add Block Randomizer toggle
        val blockRandomizerItem = createToggleItem(
            if (challenge.settings.blockRandomizer) Material.COMMAND_BLOCK else Material.FURNACE,
            "block_randomizer",
            challenge.settings.blockRandomizer,
            player
        )
        inventory.setItem(12, blockRandomizerItem)
        
        // Add Level WorldBorder toggle
        val levelWorldBorderItem = createToggleItem(
            if (challenge.settings.levelWorldBorder) Material.EXPERIENCE_BOTTLE else Material.BARRIER,
            "level_world_border",
            challenge.settings.levelWorldBorder,
            player
        )
        inventory.setItem(13, levelWorldBorderItem)
        
        // Add Starter Kit selection
        val kitMaterial = when (challenge.settings.starterKit) {
            li.angu.challengeplugin.models.StarterKit.NONE -> Material.BARRIER
            li.angu.challengeplugin.models.StarterKit.BASIC -> Material.STONE_PICKAXE
            li.angu.challengeplugin.models.StarterKit.STONE -> Material.LEATHER_CHESTPLATE
            li.angu.challengeplugin.models.StarterKit.IRON -> Material.IRON_CHESTPLATE
            li.angu.challengeplugin.models.StarterKit.DIAMOND -> Material.DIAMOND_CHESTPLATE
        }
        val starterKitItem = createCycleItem(
            kitMaterial,
            "starter_kit",
            challenge.settings.starterKit.toString().lowercase(),
            player
        )
        inventory.setItem(14, starterKitItem)

        // Add Start Challenge button
        val startItem = ItemStack(Material.DIAMOND_SWORD)
        val startMeta = startItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.DIAMOND_SWORD)
        startMeta.setDisplayName(plugin.languageManager.getMessage("challenge.settings.start", player))
        startMeta.lore = listOf(plugin.languageManager.getMessage("challenge.settings.start_lore", player))
        startItem.itemMeta = startMeta
        inventory.setItem(16, startItem)

        // Store the relationship between player and challenge
        pendingChallenges[player.uniqueId] = challenge
        playerSettingsInventories[player.uniqueId] = challenge.id
        settingsInventories[player.uniqueId] = inventory

        player.openInventory(inventory)
    }

    private fun createToggleItem(material: Material, nameKey: String, enabled: Boolean, player: Player): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)

        meta.setDisplayName(plugin.languageManager.getMessage("challenge.settings.$nameKey", player))

        val statusKey = if (enabled) "enabled" else "disabled"
        meta.lore = listOf(
            plugin.languageManager.getMessage("challenge.settings.status", player, "status" to
                plugin.languageManager.getMessage("challenge.settings.$statusKey", player)),
            plugin.languageManager.getMessage("challenge.settings.click_to_toggle", player)
        )

        item.itemMeta = meta
        return item
    }
    
    private fun createCycleItem(material: Material, nameKey: String, value: String, player: Player): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)

        meta.setDisplayName(plugin.languageManager.getMessage("challenge.settings.$nameKey", player))

        meta.lore = listOf(
            plugin.languageManager.getMessage("challenge.settings.status", player, "status" to
                plugin.languageManager.getMessage("challenge.settings.kit.$value", player)),
            plugin.languageManager.getMessage("challenge.settings.click_to_cycle", player)
        )

        item.itemMeta = meta
        return item
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

        when (event.slot) {
            // Natural Regeneration toggle
            10 -> {
                challenge.settings.naturalRegeneration = !challenge.settings.naturalRegeneration

                // Update item to reflect new setting
                val newItem = createToggleItem(
                    if (challenge.settings.naturalRegeneration) Material.GOLDEN_APPLE else Material.ROTTEN_FLESH,
                    "natural_regeneration",
                    challenge.settings.naturalRegeneration,
                    player
                )
                event.inventory.setItem(10, newItem)

                player.playSound(player.location, "minecraft:ui.button.click", 1.0f, 1.0f)
            }
            
            // Sync Hearts toggle
            11 -> {
                challenge.settings.syncHearts = !challenge.settings.syncHearts

                // Update item to reflect new setting
                val newItem = createToggleItem(
                    if (challenge.settings.syncHearts) Material.TOTEM_OF_UNDYING else Material.WITHER_SKELETON_SKULL,
                    "sync_hearts",
                    challenge.settings.syncHearts,
                    player
                )
                event.inventory.setItem(11, newItem)

                player.playSound(player.location, "minecraft:ui.button.click", 1.0f, 1.0f)
            }
            
            // Block Randomizer toggle
            12 -> {
                challenge.settings.blockRandomizer = !challenge.settings.blockRandomizer

                // Update item to reflect new setting
                val newItem = createToggleItem(
                    if (challenge.settings.blockRandomizer) Material.COMMAND_BLOCK else Material.FURNACE,
                    "block_randomizer",
                    challenge.settings.blockRandomizer,
                    player
                )
                event.inventory.setItem(12, newItem)

                player.playSound(player.location, "minecraft:ui.button.click", 1.0f, 1.0f)
            }
            
            // Level WorldBorder toggle
            13 -> {
                challenge.settings.levelWorldBorder = !challenge.settings.levelWorldBorder

                // Update item to reflect new setting
                val newItem = createToggleItem(
                    if (challenge.settings.levelWorldBorder) Material.EXPERIENCE_BOTTLE else Material.BARRIER,
                    "level_world_border",
                    challenge.settings.levelWorldBorder,
                    player
                )
                event.inventory.setItem(13, newItem)

                player.playSound(player.location, "minecraft:ui.button.click", 1.0f, 1.0f)
            }
            
            // Starter Kit cycle
            14 -> {
                // Cycle through the available kits
                challenge.settings.starterKit = when (challenge.settings.starterKit) {
                    li.angu.challengeplugin.models.StarterKit.NONE -> li.angu.challengeplugin.models.StarterKit.BASIC
                    li.angu.challengeplugin.models.StarterKit.BASIC -> li.angu.challengeplugin.models.StarterKit.STONE
                    li.angu.challengeplugin.models.StarterKit.STONE -> li.angu.challengeplugin.models.StarterKit.IRON
                    li.angu.challengeplugin.models.StarterKit.IRON -> li.angu.challengeplugin.models.StarterKit.DIAMOND
                    li.angu.challengeplugin.models.StarterKit.DIAMOND -> li.angu.challengeplugin.models.StarterKit.NONE
                }
                
                // Update item to reflect new kit
                val kitMaterial = when (challenge.settings.starterKit) {
                    li.angu.challengeplugin.models.StarterKit.NONE -> Material.BARRIER
                    li.angu.challengeplugin.models.StarterKit.BASIC -> Material.STONE_PICKAXE
                    li.angu.challengeplugin.models.StarterKit.STONE -> Material.LEATHER_CHESTPLATE
                    li.angu.challengeplugin.models.StarterKit.IRON -> Material.IRON_CHESTPLATE
                    li.angu.challengeplugin.models.StarterKit.DIAMOND -> Material.DIAMOND_CHESTPLATE
                }
                
                val newItem = createCycleItem(
                    kitMaterial,
                    "starter_kit",
                    challenge.settings.starterKit.toString().lowercase(),
                    player
                )
                event.inventory.setItem(13, newItem)
                
                player.playSound(player.location, "minecraft:ui.button.click", 1.0f, 1.0f)
            }

            // Start Challenge button
            16 -> {
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
            }
        }
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