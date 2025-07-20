package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.ConversationFactory
import org.bukkit.conversations.Prompt
import org.bukkit.conversations.StringPrompt

class CreateCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    private val conversationFactory = ConversationFactory(plugin)
        .withModality(true)
        .withFirstPrompt(NamePrompt())
        .withEscapeSequence("cancel")
        .withLocalEcho(true)
        .thatExcludesNonPlayersWithMessage("Only players can use this command")

    override fun execute(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            // No name provided, start conversation
            
            // Show a title to grab player's attention
            player.sendTitle(
                plugin.languageManager.getMessage("challenge.name_title", player),
                plugin.languageManager.getMessage("challenge.name_subtitle", player),
                10, 70, 20
            )
            
            // Play a sound to enhance the notification
            player.playSound(player.location, "minecraft:entity.experience_orb.pickup", 1.0f, 1.0f)
            
            // Send chat message with instructions
            player.sendMessage(plugin.languageManager.getMessage("challenge.name_prompt", player))
            
            // Start the conversation for name input
            conversationFactory.buildConversation(player).begin()
            return true
        }

        // Name was provided via command, use it directly
        val name = args.joinToString(" ")
        createChallengeWithName(player, name)
        return true
    }
    
    private fun createChallengeWithName(player: Player, name: String) {
        val challenge = plugin.challengeManager.createChallenge(name, player)
        
        // Open settings GUI using the new SettingsInventoryManager
        plugin.settingsInventoryManager.openSettingsInventory(player, challenge)
    }
    
    private inner class NamePrompt : StringPrompt() {
        override fun getPromptText(context: ConversationContext): String {
            return plugin.languageManager.getMessage("challenge.name_enter", context.forWhom as Player)
        }

        override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
            val player = context.forWhom as Player
            
            if (input.isNullOrBlank()) {
                player.sendMessage(plugin.languageManager.getMessage("challenge.name_invalid", player))
                return Prompt.END_OF_CONVERSATION
            }
            
            // Create challenge with the provided name
            createChallengeWithName(player, input)
            return Prompt.END_OF_CONVERSATION
        }
    }
}