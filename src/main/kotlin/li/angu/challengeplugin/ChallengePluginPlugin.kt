package li.angu.challengeplugin

import org.bukkit.plugin.java.JavaPlugin
import li.angu.challengeplugin.commands.*
import li.angu.challengeplugin.managers.ChallengeManager
import li.angu.challengeplugin.managers.PlayerDataManager
import li.angu.challengeplugin.managers.ChallengeSettingsManager
import li.angu.challengeplugin.managers.ChallengeMenuManager
import li.angu.challengeplugin.managers.LobbyManager
import li.angu.challengeplugin.managers.WorldPreparationManager
import li.angu.challengeplugin.listeners.DragonDefeatListener
import li.angu.challengeplugin.listeners.PlayerConnectionListener
import li.angu.challengeplugin.listeners.PlayerHealthListener
import li.angu.challengeplugin.listeners.BlockDropListener
import li.angu.challengeplugin.listeners.PortalListener
import li.angu.challengeplugin.listeners.ExperienceBorderListener
import li.angu.challengeplugin.tasks.TimerTask
import li.angu.challengeplugin.utils.LanguageManager

open class ChallengePluginPlugin : JavaPlugin() {

    open lateinit var challengeManager: ChallengeManager
    open lateinit var languageManager: LanguageManager
    open lateinit var playerDataManager: PlayerDataManager
    open lateinit var challengeSettingsManager: ChallengeSettingsManager
    open lateinit var challengeMenuManager: ChallengeMenuManager
    open lateinit var lobbyManager: LobbyManager
    open lateinit var blockDropListener: BlockDropListener
    open lateinit var worldPreparationManager: WorldPreparationManager

    /**
     * Helper method to register a command with its executor and tab completer
     */
    private fun registerCommand(name: String, executor: BaseCommand) {
        val command = getCommand(name)
        if (command != null) {
            command.setExecutor(executor)
            command.tabCompleter = executor
            logger.info("$name command executor and tab completer set")
        } else {
            logger.warning("Failed to register $name command - not found in plugin.yml")
        }
    }

    override open fun onEnable() {
        // Make sure the data folder exists
        dataFolder.mkdirs()

        // Initialize managers
        languageManager = LanguageManager(this)
        playerDataManager = PlayerDataManager(this)
        challengeManager = ChallengeManager(this)
        challengeSettingsManager = ChallengeSettingsManager(this)
        challengeMenuManager = ChallengeMenuManager(this)
        worldPreparationManager = WorldPreparationManager(this)

        // Initialize lobby manager
        lobbyManager = LobbyManager(this)
        lobbyManager.initialize()

        // Register commands
        // Main challenge commands
        registerCommand("create", CreateCommand(this))
        registerCommand("list", ListCommand(this))
        registerCommand("join", JoinCommand(this))
        registerCommand("leave", LeaveCommand(this))
        registerCommand("info", InfoCommand(this))
        registerCommand("delete", DeleteCommand(this))
        registerCommand("challenge", ChallengeCommand(this))
        registerCommand("prepare", PrepareWorldCommand(this))

        // Lobby commands
        registerCommand("lobby", LobbyCommand(this))
        registerCommand("setlobby", SetLobbyCommand(this))

        // Other commands
        registerCommand("lang", LanguageCommand(this))
        registerCommand("pause", PauseCommand(this))
        registerCommand("debugdragon", DebugDragonCommand(this))
        registerCommand("debugcolors", DebugColorsCommand(this))
        registerCommand("debugrespawn", DebugRespawnCommand(this))
        registerCommand("debugspawn", DebugSpawnCommand(this))

        // Register listeners
        server.pluginManager.registerEvents(DragonDefeatListener(this), this)
        server.pluginManager.registerEvents(PlayerConnectionListener(this), this)
        server.pluginManager.registerEvents(PlayerHealthListener(this), this)
        server.pluginManager.registerEvents(PortalListener(this), this)
        blockDropListener = BlockDropListener(this)
        server.pluginManager.registerEvents(blockDropListener, this)
        server.pluginManager.registerEvents(ExperienceBorderListener(this), this)

        // Start timer task for challenge duration display
        TimerTask.startTimer(this)

        logger.info(languageManager.getMessage("plugin.enabled"))
    }

    override open fun onDisable() {
        // Save any active challenges
        challengeManager.saveActiveChallenges()

        // Save randomizer mappings
        blockDropListener.saveRandomizerMappings()

        // Save player data for all online players that are in challenges
        server.onlinePlayers.forEach { player ->
            val challenge = challengeManager.getPlayerChallenge(player)
            if (challenge != null) {
                playerDataManager.savePlayerData(player, challenge.id)
            }
        }

        // Cleanup managers
        challengeSettingsManager.cleanup()
        challengeMenuManager.cleanup()

        logger.info(languageManager.getMessage("plugin.disabled"))
    }
}
