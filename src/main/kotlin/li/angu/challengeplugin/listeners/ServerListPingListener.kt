package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import java.util.UUID

class ServerListPingListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler
    fun onServerListPing(event: PaperServerListPingEvent) {
        // Create attractive MOTD with Angu.li branding
        val line1 = Component.text("✦ ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text("Angu.li", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ✦ ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("since 2014", NamedTextColor.GRAY, TextDecoration.BOLD))

        val line2 = Component.text("» ", NamedTextColor.GREEN, TextDecoration.BOLD)
            .append(Component.text("New Challenge Update", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" «", NamedTextColor.GREEN, TextDecoration.BOLD))

        val motd = Component.text()
            .append(line1)
            .append(Component.newline())
            .append(line2)
            .build()

        event.motd(motd)

        // Easter egg in player count hover - customize the listed players
        val listedPlayers = event.listedPlayers
        listedPlayers.clear()

        // Add easter egg messages first
        listedPlayers.add(PaperServerListPingEvent.ListedPlayerInfo("§7─────────────────", UUID.randomUUID()))
        listedPlayers.add(PaperServerListPingEvent.ListedPlayerInfo("§6✦ Welcome to Angu.li! ✦", UUID.randomUUID()))
        listedPlayers.add(PaperServerListPingEvent.ListedPlayerInfo("§eChallenge yourself today!", UUID.randomUUID()))
        listedPlayers.add(PaperServerListPingEvent.ListedPlayerInfo("§7─────────────────", UUID.randomUUID()))

        val onlinePlayers = plugin.server.onlinePlayers

        if (onlinePlayers.isNotEmpty()) {
            // Add actual online players (limit to 8 to leave room for easter egg)
            onlinePlayers.take(8).forEach { player ->
                listedPlayers.add(PaperServerListPingEvent.ListedPlayerInfo(player.name, player.uniqueId))
            }

            // If there are more than 8 players, show count
            if (onlinePlayers.size > 8) {
                val remaining = onlinePlayers.size - 8
                listedPlayers.add(PaperServerListPingEvent.ListedPlayerInfo("§7... and $remaining more", UUID.randomUUID()))
            }
        }
    }
}
