package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Manages the lobby functionality for the challenge plugin.
 * Handles teleporting players to the lobby, setting up their inventory, and managing lobby state.
 */
class LobbyManager(private val plugin: ChallengePluginPlugin) {

    private val menuItemKey = NamespacedKey(plugin, "challenge_menu_item")
    private var lobbySpawnLocation: Location? = null
    private val lobbyConfigFile = File(plugin.dataFolder, "lobby.yml")

    /**
     * Initialize the lobby manager
     */
    fun initialize() {
        // Load saved lobby location if available
        if (lobbyConfigFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(lobbyConfigFile)

            val worldName = config.getString("world")
            val x = config.getDouble("x")
            val y = config.getDouble("y")
            val z = config.getDouble("z")
            val yaw = config.getDouble("yaw")
            val pitch = config.getDouble("pitch")

            if (worldName != null) {
                val world = plugin.server.getWorld(worldName)
                if (world != null) {
                    lobbySpawnLocation = Location(world, x, y, z, yaw.toFloat(), pitch.toFloat())
                    plugin.logger.info("Loaded lobby spawn from config: $worldName: $x, $y, $z")
                } else {
                    plugin.logger.warning("Couldn't find world '$worldName' for lobby spawn")
                }
            }
        }

        // If no lobby location was loaded, use the main world spawn
        if (lobbySpawnLocation == null) {
            val mainWorld = plugin.server.worlds.first()
            lobbySpawnLocation = mainWorld.spawnLocation
            plugin.logger.info("No saved lobby spawn found. Using main world spawn at ${mainWorld.name}: ${lobbySpawnLocation?.x}, ${lobbySpawnLocation?.y}, ${lobbySpawnLocation?.z}")
        }
    }

    /**
     * Set a custom spawn location for the lobby and save it to config
     */
    fun setLobbySpawn(location: Location) {
        lobbySpawnLocation = location.clone()

        // Save to config
        val config = YamlConfiguration()
        config.set("world", location.world?.name)
        config.set("x", location.x)
        config.set("y", location.y)
        config.set("z", location.z)
        config.set("yaw", location.yaw)
        config.set("pitch", location.pitch)

        try {
            config.save(lobbyConfigFile)
            plugin.logger.info("Lobby spawn set and saved to: ${location.world?.name}: ${location.x}, ${location.y}, ${location.z}")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save lobby spawn location: ${e.message}")
        }
    }

    /**
     * Get the current lobby spawn location
     */
    fun getLobbySpawn(): Location? {
        return lobbySpawnLocation?.clone()
    }

    /**
     * Teleport a player to the lobby and set their gamemode to survival
     * Note: This method does not automatically leave challenges - that should be handled
     * by the calling code if needed.
     */
    fun teleportToLobby(player: Player) {
        // If player is in a challenge, leave it first
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        if (challenge != null) {
            plugin.challengeManager.leaveChallenge(player)
        }

        // Teleport to lobby spawn
        lobbySpawnLocation?.let { spawn ->
            player.teleport(spawn)
            player.gameMode = GameMode.SURVIVAL
            setupLobbyInventory(player)

            // Send welcome message only for new players
            if (player.hasPlayedBefore().not()) {
                player.sendMessage(plugin.languageManager.getMessage("lobby.welcome", player))
            }
        } ?: run {
            plugin.logger.warning("Attempted to teleport ${player.name} to lobby but spawn location is not set")
        }
    }

    /**
     * Setup a player's inventory for the lobby
     */
    fun setupLobbyInventory(player: Player) {
        // Clear inventory
        player.inventory.clear()

        // Create challenge menu star
        val menuItem = createMenuItem(player)

        // Set to slot 1 (index 0)
        player.inventory.setItem(0, menuItem)
    }

    /**
     * Create the challenge menu item (nether star)
     */
    fun createMenuItem(player: Player): ItemStack {
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

    /**
     * Check if an item is our menu item
     */
    fun isMenuItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.NETHER_STAR) return false

        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(menuItemKey, PersistentDataType.BYTE)
    }

    /**
     * Check if a world is the lobby world (main world)
     */
    fun isLobbyWorld(worldName: String): Boolean {
        return lobbySpawnLocation?.world?.name == worldName
    }
}
