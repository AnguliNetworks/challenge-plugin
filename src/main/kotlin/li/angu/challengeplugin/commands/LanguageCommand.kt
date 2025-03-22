package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class LanguageCommand(private val plugin: ChallengePluginPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("command.player_only"))
            return true
        }
        
        if (args.isEmpty()) {
            // Show current language and available languages
            val currentLang = plugin.languageManager.getPlayerLanguage(sender)
            sender.sendMessage(plugin.languageManager.getMessage("language.current", sender, "lang" to currentLang))
            
            val availableLanguages = plugin.languageManager.getAvailableLanguages().joinToString(", ")
            sender.sendMessage(plugin.languageManager.getMessage("language.available", sender, "languages" to availableLanguages))
            sender.sendMessage(plugin.languageManager.getMessage("language.usage", sender))
            return true
        }
        
        val newLang = args[0].lowercase()
        if (!plugin.languageManager.getAvailableLanguages().contains(newLang)) {
            sender.sendMessage(plugin.languageManager.getMessage("language.not_available", sender, "lang" to newLang))
            
            val availableLanguages = plugin.languageManager.getAvailableLanguages().joinToString(", ")
            sender.sendMessage(plugin.languageManager.getMessage("language.available", sender, "languages" to availableLanguages))
            return true
        }
        
        plugin.languageManager.setPlayerLanguage(sender, newLang)
        sender.sendMessage(plugin.languageManager.getMessage("language.changed", sender, "lang" to newLang))
        return true
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player || args.size > 1) {
            return emptyList()
        }
        
        return plugin.languageManager.getAvailableLanguages().filter { it.startsWith(args[0].lowercase()) }
    }
}