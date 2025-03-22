package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player

/**
 * Debug command to teleport players to a specific world's spawn location
 */
class DebugSpawnCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {
    
    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Check permission
        if (!player.hasPermission("challengeplugin.debug")) {
            player.sendMessage(plugin.languageManager.getMessage("command.no_permission", player))
            return true
        }
        
        // Check if world name is provided
        if (args.isEmpty()) {
            player.sendMessage("${ChatColor.RED}Usage: /debugspawn <world_name>")
            return true
        }
        
        val worldName = args[0]
        var world = Bukkit.getWorld(worldName)
        
        if (world == null) {
            // World not loaded, try to load it
            player.sendMessage("${ChatColor.YELLOW}World '$worldName' is not loaded. Attempting to load it...")
            
            try {
                world = Bukkit.createWorld(WorldCreator(worldName))
                
                if (world == null) {
                    player.sendMessage("${ChatColor.RED}Failed to load world '$worldName'. The world might not exist.")
                    player.sendMessage("${ChatColor.RED}Available worlds:")
                    Bukkit.getWorlds().forEach { 
                        player.sendMessage("${ChatColor.GRAY}- ${it.name}")
                    }
                    
                    // List challenge worlds that might not be loaded
                    val challengeWorlds = getAllChallengeWorldNames()
                    if (challengeWorlds.isNotEmpty()) {
                        player.sendMessage("${ChatColor.YELLOW}Challenge worlds (might need to be loaded):")
                        challengeWorlds.forEach {
                            player.sendMessage("${ChatColor.GRAY}- $it")
                        }
                    }
                    
                    return true
                }
                
                player.sendMessage("${ChatColor.GREEN}Successfully loaded world '$worldName'.")
            } catch (e: Exception) {
                player.sendMessage("${ChatColor.RED}Error loading world '$worldName': ${e.message}")
                return true
            }
        }
        
        // Teleport to world spawn
        player.teleport(world.spawnLocation)
        player.sendMessage("${ChatColor.GREEN}Teleported to spawn point of world '${world.name}'.")
        
        return true
    }
    
    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size == 1) {
            // Get all currently loaded worlds
            val loadedWorlds = Bukkit.getWorlds().map { it.name }
            
            // Get all challenge world names (including those not loaded)
            val challengeWorlds = getAllChallengeWorldNames()
            
            // Combine both lists and filter based on input
            return (loadedWorlds + challengeWorlds)
                .distinct() // Remove duplicates
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
    
    /**
     * Gets all challenge world names, including those that might not be loaded
     */
    private fun getAllChallengeWorldNames(): List<String> {
        val worlds = mutableListOf<String>()
        
        // Add all challenge worlds from the challenge manager
        plugin.challengeManager.getAllChallenges().forEach { challenge ->
            // Add the main world
            worlds.add(challenge.worldName)
            
            // Add nether and end worlds
            worlds.add("${challenge.worldName}_nether")
            worlds.add("${challenge.worldName}_the_end")
        }
        
        return worlds
    }
}