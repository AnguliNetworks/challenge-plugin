package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.entity.Player
import java.util.UUID

class InfoCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        val challenge = if (args.isNotEmpty()) {
            try {
                val id = UUID.fromString(args[0])
                plugin.challengeManager.getChallenge(id)
            } catch (e: IllegalArgumentException) {
                player.sendMessage(plugin.languageManager.getMessage("challenge.invalid_id", player))
                return true
            }
        } else {
            plugin.challengeManager.getPlayerChallenge(player)
        }

        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_found", player))
            return true
        }

        player.sendMessage(plugin.languageManager.getMessage("command.info.title", player, "name" to challenge.name))
        player.sendMessage(plugin.languageManager.getMessage("command.info.id", player, "id" to challenge.id.toString()))
        
        val statusKey = when(challenge.status) {
            ChallengeStatus.ACTIVE -> "status.active"
            ChallengeStatus.COMPLETED -> "status.completed"
            ChallengeStatus.FAILED -> "status.failed"
        }
        player.sendMessage(plugin.languageManager.getMessage("command.info.status", player, "status" to plugin.languageManager.getMessage(statusKey, player)))
        
        player.sendMessage(plugin.languageManager.getMessage("command.info.world", player, "world" to challenge.worldName))
        player.sendMessage(plugin.languageManager.getMessage("command.info.players", player, "count" to challenge.players.size.toString()))
        
        val playerNames = challenge.players.mapNotNull { 
            plugin.server.getOfflinePlayer(it).name 
        }.joinToString(", ")
        
        player.sendMessage(plugin.languageManager.getMessage("command.info.player_names", player, "names" to playerNames))
        
        // Show duration
        if (challenge.startedAt != null) {
            player.sendMessage(plugin.languageManager.getMessage("command.info.duration", player, "time" to challenge.getFormattedDuration()))
        }
        
        if (challenge.status != ChallengeStatus.ACTIVE && challenge.completedAt != null) {
            player.sendMessage(plugin.languageManager.getMessage("command.info.completed_at", player, "date" to challenge.completedAt.toString()))
        }
        
        return true
    }

    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return plugin.challengeManager.getAllChallenges()
                .map { it.id.toString() }
                .filter { it.startsWith(args[0]) }
        }
        return emptyList()
    }
}