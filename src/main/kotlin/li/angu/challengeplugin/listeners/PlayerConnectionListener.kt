package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Handles player connection events (join/quit) to manage player data persistence
 * for challenges across sessions.
 */
class PlayerConnectionListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Set custom join message
        event.joinMessage = plugin.languageManager.getMessage("connection.join", player, "player" to player.name)

        // Always teleport player to the lobby first
        plugin.lobbyManager.teleportToLobby(player)

        // Send welcome message
        player.sendMessage(plugin.languageManager.getMessage("lobby.welcome", player))
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val challenge = plugin.challengeManager.getPlayerChallenge(player)

        // Set custom quit message
        event.quitMessage = plugin.languageManager.getMessage("connection.leave", player, "player" to player.name)

        // Save player data if they're in an active challenge
        if (challenge != null && challenge.isPlayerInChallenge(player)) {
            // Save player data for the challenge
            plugin.playerDataManager.savePlayerData(player, challenge.id)

            // Remove the player from the challenge to pause timer if needed
            challenge.removePlayer(player)

            // Remove the player from the playerChallengeMap
            // This ensures they'll start in the lobby when reconnecting
            plugin.challengeManager.removePlayerFromChallenge(player.uniqueId)
        }
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from
        val toWorld = player.world

        val challenge = plugin.challengeManager.getPlayerChallenge(player)

        // Check if player entered the lobby world
        if (plugin.lobbyManager.isLobbyWorld(toWorld.name)) {
            // Always reset player stats and inventory when entering lobby
            player.gameMode = org.bukkit.GameMode.SURVIVAL
            plugin.lobbyManager.setupLobbyInventory(player)
        } else {
            // Player left the lobby world, remove flight gear
            plugin.elytraManager.removeLobbyFlightGear(player)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        // Check if the item is our menu item
        if (plugin.lobbyManager.isMenuItem(item) &&
            (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            event.isCancelled = true

            // Open the menu
            plugin.challengeMenuManager.openMainMenu(player)
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        
        // Check if player is in lobby world
        if (plugin.lobbyManager.isLobbyWorld(player.world.name)) {
            // Cancel all damage in lobby
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val to = event.to ?: return
        
        // Check if player is in lobby world and fell below y=0
        if (plugin.lobbyManager.isLobbyWorld(player.world.name) && to.y < 0) {
            // Teleport back to lobby spawn
            plugin.lobbyManager.getLobbySpawn()?.let { spawn ->
                player.teleport(spawn)
            }
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
}
