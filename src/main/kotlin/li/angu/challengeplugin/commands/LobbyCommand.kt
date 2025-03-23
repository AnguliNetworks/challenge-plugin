package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to teleport to the lobby and leave any active challenge
 */
class LobbyCommand(private val plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("command.player_only", null))
            return true
        }

        val player = sender
        
        // Check if player is in a challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        if (challenge != null) {
            // Save player data before leaving
            plugin.playerDataManager.savePlayerData(player, challenge.id)
            
            // Leave the challenge and inform the player
            if (plugin.challengeManager.leaveChallenge(player)) {
                player.sendMessage(plugin.languageManager.getMessage("challenge.left", player, "name" to challenge.name))
            }
        }
        
        // Teleport the player to the lobby
        plugin.lobbyManager.teleportToLobby(player)
        player.sendMessage(plugin.languageManager.getMessage("lobby.teleported", player))
        
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        // No tab completion for this command
        return emptyList()
    }
}