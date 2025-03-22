package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.entity.Player

class ListCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        val challenges = plugin.challengeManager.getAllChallenges()
        
        if (challenges.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.no_challenges", player))
            return true
        }

        player.sendMessage(plugin.languageManager.getMessage("command.list.title", player))
        challenges.forEach { challenge ->
            val status = plugin.languageManager.getMessage(
                when(challenge.status) {
                    ChallengeStatus.ACTIVE -> "status.active"
                    ChallengeStatus.COMPLETED -> "status.completed"
                    ChallengeStatus.FAILED -> "status.failed"
                }, player
            )
            
            player.sendMessage(
                plugin.languageManager.getMessage("command.list.format", player,
                    "name" to challenge.name,
                    "id" to challenge.id.toString(),
                    "status" to status,
                    "player_count" to challenge.players.size.toString()
                )
            )
        }
        return true
    }
}