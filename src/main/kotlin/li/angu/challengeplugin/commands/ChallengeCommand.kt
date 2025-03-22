package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player

class ChallengeCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Always open the challenge menu, ignoring any arguments
        plugin.challengeMenuManager.openMainMenu(player)
        return true
    }
    
    // No tab completions needed as the command doesn't use arguments
    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        return emptyList()
    }
}