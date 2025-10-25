package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Command to create and manage challenge NPCs
 * Usage: /npc create <challenge_id>
 *        /npc delete <npc_id>
 */
class NPCCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.npc", player))
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(player, args)
            "delete" -> handleDelete(player, args)
            else -> player.sendMessage(plugin.languageManager.getMessage("command.usage.npc", player))
        }

        return true
    }

    private fun handleCreate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.npc_create", player))
            return
        }

        // Parse challenge ID
        val challengeId: UUID
        try {
            challengeId = UUID.fromString(args[1])
        } catch (e: IllegalArgumentException) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "challenge.invalid_id",
                    player
                )
            )
            return
        }

        // Verify challenge exists
        val challenge = plugin.challengeManager.getChallenge(challengeId)
        if (challenge == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "npc.challenge_not_found",
                    player,
                    "id" to challengeId.toString()
                )
            )
            return
        }

        // Create NPC at player's location
        val npcId = plugin.npcManager.createNPC(challengeId, player.location)
        if (npcId != null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "npc.created",
                    player,
                    "name" to challenge.name,
                    "npc_id" to npcId.toString().substring(0, 8)
                )
            )
        } else {
            player.sendMessage(plugin.languageManager.getMessage("npc.create_failed", player))
        }
    }

    private fun handleDelete(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.languageManager.getMessage("command.usage.npc_delete", player))
            return
        }

        // Parse NPC ID
        val npcId: UUID
        try {
            npcId = UUID.fromString(args[1])
        } catch (e: IllegalArgumentException) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "npc.invalid_id",
                    player
                )
            )
            return
        }

        // Delete the NPC
        val success = plugin.npcManager.deleteNPC(npcId)
        if (success) {
            player.sendMessage(plugin.languageManager.getMessage("npc.deleted", player))
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "npc.not_found",
                    player,
                    "id" to npcId.toString().substring(0, 8)
                )
            )
        }
    }

    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("create", "delete").filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                when (args[0].lowercase()) {
                    "create" -> {
                        // Suggest challenge IDs
                        plugin.challengeManager.getAllChallenges()
                            .map { it.id.toString() }
                            .filter { it.startsWith(args[1]) }
                    }
                    "delete" -> {
                        // Could suggest NPC IDs from database, but that's complex
                        // For now, return empty list
                        emptyList()
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
