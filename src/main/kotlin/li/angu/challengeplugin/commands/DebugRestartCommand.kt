package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import java.util.UUID

class DebugRestartCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        plugin.logger.info("[DEBUG] DebugRestartCommand executed by ${player.name}")

        // Check permission
        if (!player.hasPermission("challengeplugin.debug")) {
            plugin.logger.info("[DEBUG] Player ${player.name} lacks permission")
            player.sendMessage(plugin.languageManager.getMessage("command.debug.no_permission", player))
            return true
        }

        // Check arguments
        if (args.isEmpty()) {
            plugin.logger.info("[DEBUG] No arguments provided")
            player.sendMessage(plugin.languageManager.getMessage("command.debug.restart_usage", player))
            return true
        }

        plugin.logger.info("[DEBUG] Arguments: ${args.joinToString(", ")}")

        // Parse challenge ID
        val challengeId = try {
            UUID.fromString(args[0])
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("[DEBUG] Invalid UUID format: ${args[0]}")
            player.sendMessage(plugin.languageManager.getMessage("challenge.invalid_id", player))
            return true
        }

        plugin.logger.info("[DEBUG] Parsed challenge ID: $challengeId")

        // Restart the challenge (this will load from DB if not in memory)
        plugin.logger.info("[DEBUG] Calling restartChallenge...")
        val result = plugin.challengeManager.restartChallenge(challengeId)
        plugin.logger.info("[DEBUG] restartChallenge returned: $result")

        when {
            result == null -> {
                player.sendMessage(plugin.languageManager.getMessage("challenge.not_found", player))
            }
            result -> {
                // Get the challenge name for the success message
                val challenge = plugin.challengeManager.getChallenge(challengeId)
                val challengeName = challenge?.name ?: challengeId.toString()

                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "command.debug.restart_success",
                        player,
                        "name" to challengeName,
                        "id" to challengeId.toString()
                    )
                )
            }
            else -> {
                player.sendMessage(plugin.languageManager.getMessage("command.debug.restart_failed", player))
            }
        }

        return true
    }

    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size == 1) {
            // Suggest challenge IDs
            return plugin.challengeManager.getAllChallenges()
                .filter { it.name.lowercase().contains(args[0].lowercase()) || it.id.toString().startsWith(args[0]) }
                .map { it.id.toString() }
        }
        return emptyList()
    }
}
