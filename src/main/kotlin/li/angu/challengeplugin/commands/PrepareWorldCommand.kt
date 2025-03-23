package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.managers.WorldPreparationManager
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class PrepareWorldCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Only allow server operators to generate worlds
        if (!player.isOp()) {
            player.sendMessage("${ChatColor.RED}You don't have permission to prepare worlds.")
            return true
        }

        // Get access to the WorldPreparationManager
        val worldManager = plugin.challengeManager.getWorldPreparationManager()

        // Show current world count
        val currentCount = worldManager.countAvailableWorlds()
        player.sendMessage("${ChatColor.GREEN}Currently have $currentCount pregenerated worlds available.")

        // If no arguments provided, just show the count and exit
        if (args.isEmpty()) {
            return true
        }

        // Parse the count argument
        var count = 1
        try {
            count = args[0].toInt()
            if (count <= 0) {
                player.sendMessage("${ChatColor.RED}Count must be a positive number.")
                return true
            }
        } catch (e: NumberFormatException) {
            player.sendMessage("${ChatColor.RED}Invalid number format. Usage: /prepare [count]")
            return true
        }

        // Start generation
        player.sendMessage("${ChatColor.YELLOW}Starting generation of $count world(s)...")

        // Start generating worlds one by one to avoid overloading the server
        var generated = 0

        generateNextWorld(player, worldManager, generated, count)

        return true
    }

    private fun generateNextWorld(player: Player, worldManager: WorldPreparationManager, generated: Int, total: Int) {
        if (generated >= total) {
            // After finishing, show updated count
            val currentCount = worldManager.countAvailableWorlds()
            player.sendMessage("${ChatColor.GREEN}All $total worlds have been prepared successfully!")
            player.sendMessage("${ChatColor.GREEN}Now have $currentCount pregenerated worlds available.")
            return
        }

        val currentNum = generated + 1
        player.sendMessage("${ChatColor.YELLOW}Generating world $currentNum of $total...")

        worldManager.prepareWorldAsync().thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    player.sendMessage("${ChatColor.GREEN}World $currentNum/$total generated successfully.")
                    // Generate the next world
                    generateNextWorld(player, worldManager, currentNum, total)
                } else {
                    player.sendMessage("${ChatColor.RED}Failed to generate world $currentNum/$total. Stopping generation.")
                    // Show current count after failure
                    val currentCount = worldManager.countAvailableWorlds() / 3
                    player.sendMessage("${ChatColor.GREEN}Currently have $currentCount pregenerated worlds available.")
                }
            })
        }
    }
}
