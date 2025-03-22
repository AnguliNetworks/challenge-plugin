package li.angu.challengeplugin

import org.bukkit.plugin.java.JavaPlugin
import li.angu.challengeplugin.commands.ChallengeCommand
import li.angu.challengeplugin.commands.LanguageCommand
import li.angu.challengeplugin.commands.DebugDragonCommand
import li.angu.challengeplugin.commands.DebugColorsCommand
import li.angu.challengeplugin.managers.ChallengeManager
import li.angu.challengeplugin.managers.PlayerDataManager
import li.angu.challengeplugin.managers.ChallengeSettingsManager
import li.angu.challengeplugin.listeners.DragonDefeatListener
import li.angu.challengeplugin.listeners.PlayerConnectionListener
import li.angu.challengeplugin.tasks.TimerTask
import li.angu.challengeplugin.utils.LanguageManager

open class ChallengePluginPlugin : JavaPlugin() {

    open lateinit var challengeManager: ChallengeManager
    open lateinit var languageManager: LanguageManager
    open lateinit var playerDataManager: PlayerDataManager
    open lateinit var challengeSettingsManager: ChallengeSettingsManager

    override open fun onEnable() {
        // Make sure the data folder exists
        dataFolder.mkdirs()

        // Initialize managers
        languageManager = LanguageManager(this)
        playerDataManager = PlayerDataManager(this)
        challengeManager = ChallengeManager(this)
        challengeSettingsManager = ChallengeSettingsManager(this)

        // Register commands
        val challengeCommand = getCommand("challenge")
        val langCommand = getCommand("lang")
        val debugDragonCommand = getCommand("debugdragon")
        val debugColorsCommand = getCommand("debugcolors")
        
        // Log command registration status and available commands
        logger.info("Challenge command registration: ${challengeCommand != null}")
        logger.info("Lang command registration: ${langCommand != null}")
        logger.info("Debug Dragon command registration: ${debugDragonCommand != null}")
        logger.info("Debug Colors command registration: ${debugColorsCommand != null}")
        
        // Log all commands registered with this plugin
        getDescription().commands.forEach { (name, _) ->
            logger.info("Plugin has command registered: $name")
        }
        
        // Set command executors and tab completers if commands are registered
        if (challengeCommand != null) {
            val challengeCommandExecutor = ChallengeCommand(this)
            challengeCommand.setExecutor(challengeCommandExecutor)
            challengeCommand.tabCompleter = challengeCommandExecutor
            logger.info("Challenge command executor and tab completer set")
        }
        
        if (langCommand != null) {
            val langCommandExecutor = LanguageCommand(this)
            langCommand.setExecutor(langCommandExecutor)
            langCommand.tabCompleter = langCommandExecutor
            logger.info("Lang command executor and tab completer set")
        }
        
        if (debugDragonCommand != null) {
            val debugDragonCommandExecutor = DebugDragonCommand(this)
            debugDragonCommand.setExecutor(debugDragonCommandExecutor)
            debugDragonCommand.tabCompleter = debugDragonCommandExecutor
            logger.info("Debug Dragon command executor and tab completer set")
        }
        
        if (debugColorsCommand != null) {
            val debugColorsCommandExecutor = DebugColorsCommand(this)
            debugColorsCommand.setExecutor(debugColorsCommandExecutor)
            debugColorsCommand.tabCompleter = debugColorsCommandExecutor
            logger.info("Debug Colors command executor and tab completer set")
        }

        // Register listeners
        server.pluginManager.registerEvents(DragonDefeatListener(this), this)
        server.pluginManager.registerEvents(PlayerConnectionListener(this), this)

        // Start timer task for challenge duration display
        TimerTask.startTimer(this)

        logger.info(languageManager.getMessage("plugin.enabled"))
    }

    override open fun onDisable() {
        // Save any active challenges
        challengeManager.saveActiveChallenges()
        
        // Save player data for all online players that are in challenges
        server.onlinePlayers.forEach { player ->
            val challenge = challengeManager.getPlayerChallenge(player)
            if (challenge != null) {
                playerDataManager.savePlayerData(player, challenge.id)
            }
        }
        
        // Cleanup the settings manager
        challengeSettingsManager.cleanup()

        logger.info(languageManager.getMessage("plugin.disabled"))
    }
}
