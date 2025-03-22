package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player

class CreateCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.create", player))
            return true
        }

        val name = args.joinToString(" ")
        val challenge = plugin.challengeManager.createChallenge(name)
        
        // Open settings GUI
        plugin.challengeSettingsManager.openSettingsInventory(player, challenge)
        return true
    }
}