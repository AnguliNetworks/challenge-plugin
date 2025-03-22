package li.angu.challengeplugin.tasks

import li.angu.challengeplugin.ChallengePluginPlugin
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable

class TimerTask(private val plugin: ChallengePluginPlugin) : BukkitRunnable() {

    override fun run() {
        // For each online player, check if they're in a challenge
        for (player in Bukkit.getOnlinePlayers()) {
            val challenge = plugin.challengeManager.getPlayerChallenge(player)
            if (challenge?.startedAt == null) {
                continue
            }

            // Get the formatted duration for the challenge
            val formattedTime = challenge.getFormattedDuration()

            // Send action bar message with localized text
            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent(plugin.languageManager.getMessage("challenge.duration_display", player, "time" to formattedTime))
            )
        }
    }

    companion object {
        fun startTimer(plugin: ChallengePluginPlugin) {
            // Start the timer display task (runs every second)
            TimerTask(plugin).runTaskTimer(plugin, 0L, 20L) // Update every second
            
            // Start the world unload check task (runs every minute)
            WorldUnloadTask(plugin).runTaskTimer(plugin, 20L * 60L, 20L * 60L)
        }
    }
}

class WorldUnloadTask(private val plugin: ChallengePluginPlugin) : BukkitRunnable() {
    
    override fun run() {
        // Get all active challenges
        val challenges = plugin.challengeManager.getActiveChallenges()
        
        for (challenge in challenges) {
            // Check if the challenge has been empty for 5+ minutes
            if (challenge.isReadyForUnload()) {
                // Unload the challenge world and its associated dimensions
                unloadChallengeWorlds(challenge.worldName)
                
                plugin.logger.info("Unloaded worlds for challenge ${challenge.name} (${challenge.id}) due to inactivity")
            }
        }
    }
    
    private fun unloadChallengeWorlds(worldName: String) {
        // Try to unload the main world
        val mainWorld = Bukkit.getWorld(worldName)
        if (mainWorld != null && Bukkit.unloadWorld(mainWorld, true)) {
            plugin.logger.info("Unloaded main world: $worldName")
        }
        
        // Try to unload the nether world
        val netherName = "${worldName}_nether"
        val netherWorld = Bukkit.getWorld(netherName)
        if (netherWorld != null && Bukkit.unloadWorld(netherWorld, true)) {
            plugin.logger.info("Unloaded nether world: $netherName")
        }
        
        // Try to unload the end world
        val endName = "${worldName}_the_end"
        val endWorld = Bukkit.getWorld(endName)
        if (endWorld != null && Bukkit.unloadWorld(endWorld, true)) {
            plugin.logger.info("Unloaded end world: $endName")
        }
    }
}