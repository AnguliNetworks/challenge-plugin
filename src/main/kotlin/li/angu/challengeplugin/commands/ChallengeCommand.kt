package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.ChallengeStatus
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class ChallengeCommand(private val plugin: ChallengePluginPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        plugin.logger.info("ChallengeCommand executed by ${sender.name} with label: $label and args: ${args.joinToString()}")
        
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("command.player_only"))
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "list" -> handleList(sender)
            "join" -> handleJoin(sender, args)
            "leave" -> handleLeave(sender)
            "info" -> handleInfo(sender, args)
            else -> showHelp(sender)
        }

        return true
    }

    private fun handleCreate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.create", player))
            return
        }

        val name = args.copyOfRange(1, args.size).joinToString(" ")
        val challenge = plugin.challengeManager.createChallenge(name)
        
        player.sendMessage(plugin.languageManager.getMessage("challenge.created", player, "name" to name))
        player.sendMessage(plugin.languageManager.getMessage("challenge.join_command", player, "id" to challenge.id.toString()))
    }

    private fun handleList(player: Player) {
        val challenges = plugin.challengeManager.getAllChallenges()
        
        if (challenges.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.no_challenges", player))
            return
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
    }

    private fun handleJoin(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.join", player))
            return
        }

        val id = try {
            UUID.fromString(args[1])
        } catch (e: IllegalArgumentException) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.invalid_id", player))
            return
        }

        val challenge = plugin.challengeManager.getChallenge(id)
        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_found", player))
            return
        }

        if (challenge.status != ChallengeStatus.ACTIVE) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.already_completed", player))
            return
        }

        if (plugin.challengeManager.joinChallenge(player, challenge)) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.joined", player, "name" to challenge.name))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("challenge.join_failed", player))
        }
    }

    private fun handleLeave(player: Player) {
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_in", player))
            return
        }

        if (plugin.challengeManager.leaveChallenge(player)) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.left", player, "name" to challenge.name))
        } else {
            player.sendMessage(plugin.languageManager.getMessage("challenge.leave_failed", player))
        }
    }

    private fun handleInfo(player: Player, args: Array<out String>) {
        val challenge = if (args.size >= 2) {
            try {
                val id = UUID.fromString(args[1])
                plugin.challengeManager.getChallenge(id)
            } catch (e: IllegalArgumentException) {
                player.sendMessage(plugin.languageManager.getMessage("challenge.invalid_id", player))
                return
            }
        } else {
            plugin.challengeManager.getPlayerChallenge(player)
        }

        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("challenge.not_found", player))
            return
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
    }

    private fun showHelp(player: Player) {
        player.sendMessage(plugin.languageManager.getMessage("command.help.title", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.create", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.list", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.join", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.leave", player))
        player.sendMessage(plugin.languageManager.getMessage("command.help.info", player))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) {
            return emptyList()
        }

        if (args.size == 1) {
            val subCommands = listOf("create", "list", "join", "leave", "info")
            return subCommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && (args[0].equals("join", ignoreCase = true) || args[0].equals("info", ignoreCase = true))) {
            return plugin.challengeManager.getAllChallenges()
                .filter { it.status == ChallengeStatus.ACTIVE }
                .map { it.id.toString() }
                .filter { it.startsWith(args[1]) }
        }

        return emptyList()
    }
}