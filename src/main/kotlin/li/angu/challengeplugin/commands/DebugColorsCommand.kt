package li.angu.challengeplugin.commands

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player

class DebugColorsCommand(plugin: ChallengePluginPlugin) : BaseCommand(plugin) {

    override fun execute(player: Player, args: Array<out String>): Boolean {
        // Check permission
        if (!player.hasPermission("challengeplugin.debug")) {
            player.sendMessage(plugin.languageManager.getMessage("command.debug.no_permission", player))
            return true
        }

        displayColorTable(player)
        return true
    }
    
    private fun displayColorTable(sender: Player) {
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
}