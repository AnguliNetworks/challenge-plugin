package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.entity.Player
import java.util.UUID

class JoinCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.join", player))
            return true
        }

        val id = try {
            UUID.fromString(args[0])
        } catch (e: IllegalArgumentException) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.invalid_id", player))
            return true
        }

        val challenge = plugin.challengeManager.getChallenge(id)
        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_found", player))
            return true
        }

        /*
        if (challenge.status != ChallengeStatus.ACTIVE) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.already_completed", player))
            return true
        }
         */

        if (plugin.challengeManager.joinChallenge(player, challenge)) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.joined", player, "name" to challenge.name))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("challenge.join_failed", player))
        }
        return true
    }

    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return plugin.challengeManager.getAllChallenges()
                .filter { it.status == ChallengeStatus.ACTIVE }
                .map { it.id.toString() }
                .filter { it.startsWith(args[0]) }
        }
        return emptyList()
    }
}
