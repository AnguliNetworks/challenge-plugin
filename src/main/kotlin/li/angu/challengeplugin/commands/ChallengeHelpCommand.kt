package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player

class ChallengeHelpCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        player.sendMessage(plugin.languageManager.getMessage("command.help.title", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.create", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.list", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.join", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.leave", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.info", player))
        
        // Only show delete command if player has permission
        if (player.hasPermission("challengeplugin.delete")) {
            player.sendMessage(plugin.languageManager.getMessage("command.help.delete", player))
        }
        
        return true
    }
}