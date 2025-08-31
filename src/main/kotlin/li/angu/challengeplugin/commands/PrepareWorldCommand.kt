package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.managers.WorldPreparationManager
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class PrepareWorldCommand(private val plugin: ChallengePluginPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Only allow server operators to generate worlds
        if (!sender.isOp) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to prepare worlds.")
            return true
        }

        // Get access to the WorldPreparationManager
        val worldManager = plugin.challengeManager.getWorldPreparationManager()

        // Show current world count
        val currentCount = worldManager.countAvailableWorlds()
        sender.sendMessage("${ChatColor.GREEN}Currently have $currentCount pregenerated worlds available.")

        // If no arguments provided, just show the count and exit
        if (args.isEmpty()) {
            return true
        }

        // Parse arguments: /prepare [count] [max]
        var count = 1
        var maxLimit: Int? = null
        
        try {
            count = args[0].toInt()
            if (count <= 0) {
                sender.sendMessage("${ChatColor.RED}Count must be a positive number.")
                return true
            }
            
            // Check for 'max' parameter
            if (args.size > 1 && args[1].lowercase() == "max") {
                maxLimit = count
                count = count - currentCount // Only generate what's needed to reach the limit
                
                if (count <= 0) {
                    sender.sendMessage("${ChatColor.GREEN}Already have $currentCount worlds, which meets or exceeds the max limit of $maxLimit.")
                    return true
                }
                
                sender.sendMessage("${ChatColor.YELLOW}Will generate $count world(s) to reach max limit of $maxLimit (currently have $currentCount).")
            }
        } catch (e: NumberFormatException) {
            sender.sendMessage("${ChatColor.RED}Invalid number format. Usage: /prepare [count] [max]")
            return true
        }

        // Start generation
        val message = if (maxLimit != null) {
            "Starting generation of $count world(s) to reach max limit of $maxLimit..."
        } else {
            "Starting generation of $count world(s)..."
        }
        sender.sendMessage("${ChatColor.YELLOW}$message")

        generateNextWorld(sender, worldManager, 0, count, maxLimit)

        return true
    }

    private fun generateNextWorld(sender: CommandSender, worldManager: WorldPreparationManager, generated: Int, total: Int, maxLimit: Int? = null) {
        // Check if we've reached the max limit before generating more
        if (maxLimit != null) {
            val currentCount = worldManager.countAvailableWorlds()
            if (currentCount >= maxLimit) {
                sender.sendMessage("${ChatColor.GREEN}Reached max limit of $maxLimit prepared worlds. Stopping generation.")
                sender.sendMessage("${ChatColor.GREEN}Currently have $currentCount pregenerated worlds available.")
                return
            }
        }
        
        if (generated >= total) {
            // After finishing, show updated count
            val currentCount = worldManager.countAvailableWorlds()
            val completionMessage = if (maxLimit != null) {
                "Generated $total worlds and reached max limit of $maxLimit!"
            } else {
                "All $total worlds have been prepared successfully!"
            }
            sender.sendMessage("${ChatColor.GREEN}$completionMessage")
            sender.sendMessage("${ChatColor.GREEN}Now have $currentCount pregenerated worlds available.")
            return
        }

        val currentNum = generated + 1
        sender.sendMessage("${ChatColor.YELLOW}Generating world $currentNum of $total...")

        worldManager.prepareWorldAsync().thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    sender.sendMessage("${ChatColor.GREEN}World $currentNum/$total generated successfully.")
                    // Generate the next world
                    generateNextWorld(sender, worldManager, currentNum, total, maxLimit)
                } else {
                    sender.sendMessage("${ChatColor.RED}Failed to generate world $currentNum/$total. Stopping generation.")
                    // Show current count after failure
                    val currentCount = worldManager.countAvailableWorlds()
                    sender.sendMessage("${ChatColor.GREEN}Currently have $currentCount pregenerated worlds available.")
                }
            })
        }
    }
}
