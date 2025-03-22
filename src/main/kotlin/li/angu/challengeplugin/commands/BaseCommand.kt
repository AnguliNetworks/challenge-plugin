package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Base class for all plugin commands
 */
abstract class BaseCommand(protected val plugin: ChallengePluginPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        plugin.logger.info("${this.javaClass.simpleName} executed by ${sender.name} with label: $label and args: ${args.joinToString()}")
        
        if (sender !is Player) {
            sender.sendMessage(plugin.languageManager.getMessage("command.player_only"))
            return true
        }
        
        return execute(sender, args)
    }
    
    /**
     * Execute the command logic
     */
    abstract fun execute(player: Player, args: Array<out String>): Boolean
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return if (sender is Player) {
            tabComplete(sender, args)
        } else {
            emptyList()
        }
    }
    
    /**
     * Provide tab completion suggestions
     */
    open fun tabComplete(player: Player, args: Array<out String>): List<String> {
        return emptyList()
    }
}