package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import java.util.UUID

class DeleteCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Check permission
        if (!player.hasPermission("challengeplugin.delete")) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.delete_no_permission", player))
            return true
        }
        
        // Check arguments
        if (args.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.delete", player))
            return true
        }
        
        // Parse challenge ID
        val id = try {
            UUID.fromString(args[0])
        } catch (e: IllegalArgumentException) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.invalid_id", player))
            return true
        }
        
        // Get challenge
        val challenge = plugin.challengeManager.getChallenge(id)
        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_found", player))
            return true
        }
        
        // Check for confirmation
        val needsConfirmation = args.size < 2 || args[1].lowercase() != "confirm"
        if (needsConfirmation) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.confirm_deletion", player,
                "name" to challenge.name,
                "id" to challenge.id.toString()
            ))
            return true
        }
        
        // Check if players are currently online in the challenge
        val hasOnlinePlayers = challenge.players.any { playerId ->
            plugin.server.getPlayer(playerId) != null
        }
        
        if (hasOnlinePlayers) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.cannot_delete_active", player))
            return true
        }
        
        // Delete the challenge
        if (plugin.challengeManager.deleteChallenge(id)) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.deleted_successfully", player, 
                "name" to challenge.name
            ))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("challenge.delete_failed", player))
        }
        
        return true
    }

    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (!player.hasPermission("challengeplugin.delete")) {
            return emptyList()
        }
        
        if (args.size == 1) {
            return plugin.challengeManager.getAllChallenges()
                .map { it.id.toString() }
                .filter { it.startsWith(args[0]) }
        }
        
        if (args.size == 2) {
            return listOf("confirm").filter { it.startsWith(args[1].lowercase()) }
        }
        
        return emptyList()
    }
}