package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/**
 * Handles chat events to customize chat appearance with color coding:
 * - Operators: Red
 * - Players in same challenge: Green  
 * - Other players: Gray
 */
class ChatListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val sender = event.player
        val message = event.message
        
        // Custom format: "Player: message"
        val format = plugin.languageManager.getMessage("chat.format", sender, 
            "player" to "%1\$s", 
            "message" to "%2\$s"
        )
        
        // Apply color formatting for each recipient
        val recipients = event.recipients.toList()
        
        for (recipient in recipients) {
            val playerColor = getPlayerColor(sender, recipient)
            val formattedPlayerName = "$playerColor${sender.name}§7"
            
            val personalizedMessage = format.replace("%1\$s", formattedPlayerName).replace("%2\$s", message)
            recipient.sendMessage(personalizedMessage)
        }
        
        // Cancel the original event to prevent default formatting
        event.isCancelled = true
    }
    
    /**
     * Determines the color code for a player name based on the recipient's perspective
     */
    private fun getPlayerColor(sender: Player, recipient: Player): String {
        // Operators are always red
        if (sender.hasPermission("challengeplugin.admin") || sender.isOp) {
            return "§c" // Red
        }
        
        // Check if both players are in the same challenge
        val senderChallenge = plugin.challengeManager.getPlayerChallenge(sender)
        val recipientChallenge = plugin.challengeManager.getPlayerChallenge(recipient)
        
        if (senderChallenge != null && recipientChallenge != null && 
            senderChallenge.id == recipientChallenge.id) {
            return "§a" // Green (same challenge)
        }
        
        // Default to gray for other players
        return "§7" // Gray
    }
}