package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player

/**
 * Debug command to teleport players to their spawn location
 */
class DebugRespawnCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Check permission
        if (!player.hasPermission("challengeplugin.debug")) {
            player.sendMessage(plugin.languageManager.getMessage("command.no_permission", player))
            return true
        }

        // Determine if we're teleporting just this player or all players
        if (args.isNotEmpty() && args[0].equals("all", ignoreCase = true)) {
            // Teleport all players to their spawn points
            teleportAllPlayers(player)
        } else {
            // Teleport just this player
            teleportPlayer(player)
        }

        return true
    }

    private fun teleportPlayer(player: Player) {
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        val spawnLocation = if (challenge != null) {
            // If player is in a challenge, we need to check for bed in the challenge world first
            val challengeWorld = plugin.server.getWorld(challenge.worldName)
            if (challengeWorld != null) {
                // Get the player's bed spawn in this specific world, if any
                val bedLocation = player.getBedSpawnLocation()

                // If bed location exists and is in this challenge world, use it
                if (bedLocation != null && bedLocation.world?.name == challengeWorld.name) {
                    bedLocation
                } else {
                    // Otherwise use the challenge world's spawn point
                    challengeWorld.spawnLocation
                }
            } else {
                // Fallback to current world if challenge world can't be found
                player.world.spawnLocation
            }
        } else {
            // If not in a challenge, use their bed or world spawn
            player.bedSpawnLocation ?: player.world.spawnLocation
        }
        player.teleport(spawnLocation)
        player.sendMessage("${ChatColor.GREEN}Teleported to your spawn point.")
    }

    private fun teleportAllPlayers(sender: Player) {
        var teleportCount = 0

        Bukkit.getOnlinePlayers().forEach { player ->
            val challenge = plugin.challengeManager.getPlayerChallenge(player)
            val spawnLocation = if (challenge != null) {
                // If player is in a challenge, use the challenge world spawn
                val challengeWorld = plugin.server.getWorld(challenge.worldName)
                player.bedSpawnLocation ?: challengeWorld?.spawnLocation ?: player.world.spawnLocation
            } else {
                // Otherwise use their bed or world spawn
                player.bedSpawnLocation ?: player.world.spawnLocation
            }
            player.teleport(spawnLocation)
            player.sendMessage("${ChatColor.GREEN}You were teleported to your spawn point by an admin.")
            teleportCount++
        }

        sender.sendMessage("${ChatColor.GREEN}Teleported $teleportCount player(s) to their spawn points.")
    }

    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("all").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
