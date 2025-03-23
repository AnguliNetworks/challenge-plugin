package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to set the lobby spawn location
 */
class SetLobbyCommand(private val plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("command.player_only", null))
            return true
        }

        // Check if the player has permission
        if (!sender.hasPermission("challengeplugin.admin")) {
            sender.sendMessage(plugin.languageManager.getMessage("command.debug.no_permission", sender))
            return true
        }

        val player = sender
        
        // Set the lobby spawn to the player's current location
        plugin.lobbyManager.setLobbySpawn(player.location)
        
        // Send confirmation message
        player.sendMessage(plugin.languageManager.getMessage("lobby.spawn_set", player))
        
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        // No tab completion for this command
        return emptyList()
    }
}