package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Player
import org.bukkit.attribute.Attribute
import org.bukkit.scheduler.BukkitRunnable

class DebugDragonCommand(private val plugin: ChallengePluginPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Only players can execute this command
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("command.player_only"))
            return true
        }

        // Check permission
        if (!sender.hasPermission("challengeplugin.debug")) {
            sender.sendMessage(plugin.languageManager.getMessage("command.debug.no_permission", sender))
            return true
        }

        val player = sender as Player
        val challenge = plugin.challengeManager.getPlayerChallenge(player)

        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("command.debug.no_challenge", player))
            return true
        }

        // Get the End world for this challenge
        val endWorld = challenge.getEndWorld()
        if (endWorld == null) {
            player.sendMessage(plugin.languageManager.getMessage("command.debug.no_end_world", player))
            return true
        }

        // Teleport player to the spawn location in the end world
        player.teleport(endWorld.spawnLocation)
        player.sendMessage(plugin.languageManager.getMessage("command.debug.dragon_waiting", player))

        // Wait 2 seconds to ensure everything has spawned
        object : BukkitRunnable() {
            override fun run() {
                // Find the Ender Dragon
                val dragon = endWorld.entities.find { it is EnderDragon } as? EnderDragon

                if (dragon == null) {
                    player.sendMessage(plugin.languageManager.getMessage("command.debug.no_dragon", player))
                    return
                }

                // Set dragon health to 1
                val maxHealth = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 200.0
                dragon.health = 1.0

                // Find and destroy all end crystals
                val crystals = endWorld.entities.filterIsInstance<EnderCrystal>()
                var destroyedCrystals = 0

                crystals.forEach { crystal ->
                    crystal.remove()
                    destroyedCrystals++
                }

                player.sendMessage(
                    plugin.languageManager.getMessage("command.debug.dragon_success", player,
                        "max_health" to maxHealth.toInt().toString(),
                        "crystal_count" to destroyedCrystals.toString()
                    )
                )
            }
        }.runTaskLater(plugin, 40) // 40 ticks = 2 seconds
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        // No tab completion options for this command
        return emptyList()
    }
}
