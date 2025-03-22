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
            TimerTask(plugin).runTaskTimer(plugin, 0L, 20L) // Update every second
        }
    }
}
