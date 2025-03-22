package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player

class LanguageCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {
    
    override fun execute(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            // Show current language and available languages
            val currentLang = plugin.languageManager.getPlayerLanguage(player)
            player.sendMessage(plugin.languageManager.getMessage("language.current", player, "lang" to currentLang))
            
            val availableLanguages = plugin.languageManager.getAvailableLanguages().joinToString(", ")
            player.sendMessage(plugin.languageManager.getMessage("language.available", player, "languages" to availableLanguages))
            player.sendMessage(plugin.languageManager.getMessage("language.usage", player))
            return true
        }
        
        val newLang = args[0].lowercase()
        if (!plugin.languageManager.getAvailableLanguages().contains(newLang)) {
            player.sendMessage(plugin.languageManager.getMessage("language.not_available", player, "lang" to newLang))
            
            val availableLanguages = plugin.languageManager.getAvailableLanguages().joinToString(", ")
            player.sendMessage(plugin.languageManager.getMessage("language.available", player, "languages" to availableLanguages))
            return true
        }
        
        plugin.languageManager.setPlayerLanguage(player, newLang)
        player.sendMessage(plugin.languageManager.getMessage("language.changed", player, "lang" to newLang))
        return true
    }
    
    override fun tabComplete(player: Player, args: Array<out String>): List<String> {
        if (args.size > 1) {
            return emptyList()
        }
        
        return plugin.languageManager.getAvailableLanguages().filter { it.startsWith(args[0].lowercase()) }
    }
}