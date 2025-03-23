package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player

class LeaveCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_in", player))
            return true
        }

        // Save player inventory, exp, health, etc. before leaving
        plugin.playerDataManager.savePlayerData(player, challenge.id)

        if (plugin.challengeManager.leaveChallenge(player)) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.left", player, "name" to challenge.name))
            
            // Teleport player to the lobby
            plugin.lobbyManager.teleportToLobby(player)
        } else {
            player.sendMessage(plugin.languageManager.getMessage("challenge.leave_failed", player))
        }
        return true
    }
}