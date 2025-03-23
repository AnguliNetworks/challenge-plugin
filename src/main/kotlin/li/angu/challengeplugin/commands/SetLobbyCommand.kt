package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to set the lobby spawn location
 */
class SetLobbyCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Check if the player has permission
        if (!player.hasPermission("challengeplugin.admin")) {
            player.sendMessage(plugin.languageManager.getMessage("command.debug.no_permission", player))
            return true
        }

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
