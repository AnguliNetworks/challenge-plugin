package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.GameRule
import org.bukkit.entity.Player

/**
 * Command to pause or resume a challenge
 * This stops entity movement by setting the random tick speed to 0 and pauses the timer
 */
class PauseCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {
    
    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Get the player's current challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        
        if (challenge == null) {
            player.sendMessage(plugin.languageManager.getMessage("command.debug.no_challenge", player))
            return true
        }
        
        // Check if we're pausing or resuming
        val resume = args.isNotEmpty() && args[0].equals("resume", ignoreCase = true)
        
        // Get all associated worlds
        val worlds = mutableListOf<org.bukkit.World>()
        
        // Add main world
        val mainWorld = plugin.server.getWorld(challenge.worldName)
        if (mainWorld != null) {
            worlds.add(mainWorld)
        }
        
        // Add nether world if it exists
        val netherWorld = challenge.getNetherWorld()
        if (netherWorld != null) {
            worlds.add(netherWorld)
        }
        
        // Add end world if it exists
        val endWorld = challenge.getEndWorld()
        if (endWorld != null) {
            worlds.add(endWorld)
        }
        
        if (resume) {
            // Resume the challenge
            challenge.resumeTimer()
            
            // Set random tick speed back to default (3)
            worlds.forEach { world ->
                world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3)
            }
            
            player.sendMessage(plugin.languageManager.getMessage("command.pause.resumed", player))
        } else {
            // Pause the challenge
            challenge.pauseTimer()
            
            // Set random tick speed to 0 to stop entity movement
            worlds.forEach { world ->
                world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0)
            }
            
            player.sendMessage(plugin.languageManager.getMessage("command.pause.paused", player))
        }
        
        // Save challenge state to persist pause status
        plugin.challengeManager.saveActiveChallenges()
        
        return true
    }
    
    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size == 1) {
            // Only suggest "resume" if the player has a challenge
            val challenge = plugin.challengeManager.getPlayerChallenge(player)
            if (challenge != null) {
                val options = mutableListOf<String>()
                
                // Only suggest "resume" if the challenge is paused
                if (challenge.pausedAt != null) {
                    if ("resume".startsWith(args[0].lowercase())) {
                        options.add("resume")
                    }
                }
                
                return options
            }
        }
        
        return emptyList()
    }
}