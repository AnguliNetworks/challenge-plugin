package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class DebugColorsCommand(private val plugin: ChallengePluginPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Check permission
        if (!sender.hasPermission("challengeplugin.debug")) {
            sender.sendMessage(plugin.languageManager.getMessage("command.debug.no_permission", sender))
            return true
        }

        displayColorTable(sender)
        return true
    }
    
    private fun displayColorTable(sender: CommandSender) {
        sender.sendMessage("§f=== §bMinecraft Chat Colors §f===")
        sender.sendMessage("§0Black §1Dark Blue §2Dark Green §3Dark Aqua")
        sender.sendMessage("§4Dark Red §5Dark Purple §6Gold §7Gray")
        sender.sendMessage("§8Dark Gray §9Blue §aGreen §bAqua")
        sender.sendMessage("§cRed §dLight Purple §eYellow §fWhite")
        
        sender.sendMessage("§f=== §bMinecraft Chat Formats §f===")
        sender.sendMessage("§kObfuscated§r §lBold§r §mStrikethrough§r")
        sender.sendMessage("§nUnderline§r §oItalic§r §rReset")
        
        sender.sendMessage("§f=== §bColor Codes §f===")
        sender.sendMessage("§0 - §00 §1 - §11 §2 - §22 §3 - §33")
        sender.sendMessage("§4 - §44 §5 - §55 §6 - §66 §7 - §77")
        sender.sendMessage("§8 - §88 §9 - §99 §a - §aa §b - §bb")
        sender.sendMessage("§c - §cc §d - §dd §e - §ee §f - §ff")
        
        sender.sendMessage("§f=== §bFormat Codes §f===")
        sender.sendMessage("§k - §kk§r §l - §ll §m - §mm")
        sender.sendMessage("§n - §nn §o - §oo §r - r")
        
        sender.sendMessage("§f=== §bCombinations §f===")
        sender.sendMessage("§a§lGreen Bold §b§nAqua Underlined §c§oRed Italic")
        sender.sendMessage("§e§mYellow Strikethrough §9§lBlue Bold §d§kLight Purple Obfuscated§r")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        // No tab completion options for this command
        return emptyList()
    }
}