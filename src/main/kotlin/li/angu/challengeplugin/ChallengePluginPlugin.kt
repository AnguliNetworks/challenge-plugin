package li.angu.challengeplugin

import org.bukkit.plugin.java.JavaPlugin
import li.angu.challengeplugin.commands.*
import li.angu.challengeplugin.database.DatabaseDriver
import li.angu.challengeplugin.managers.ChallengeManager
import li.angu.challengeplugin.managers.PlayerDataManager
import li.angu.challengeplugin.managers.SettingsInventoryManager
import li.angu.challengeplugin.managers.ChallengeMenuManager
import li.angu.challengeplugin.managers.LobbyManager
import li.angu.challengeplugin.managers.WorldPreparationManager
import li.angu.challengeplugin.managers.ElytraManager
import li.angu.challengeplugin.managers.BedSpawnManager
import li.angu.challengeplugin.managers.NPCManager
import li.angu.challengeplugin.listeners.DragonDefeatListener
import li.angu.challengeplugin.listeners.PlayerConnectionListener
import li.angu.challengeplugin.listeners.PlayerHealthListener
import li.angu.challengeplugin.listeners.BlockDropListener
import li.angu.challengeplugin.listeners.PortalListener
import li.angu.challengeplugin.listeners.ExperienceBorderListener
import li.angu.challengeplugin.listeners.LobbyProtectionListener
import li.angu.challengeplugin.listeners.ServerListPingListener
import li.angu.challengeplugin.listeners.ChatListener
import li.angu.challengeplugin.listeners.BedSpawnListener
import li.angu.challengeplugin.listeners.NPCListener
import li.angu.challengeplugin.tasks.TimerTask
import li.angu.challengeplugin.utils.LanguageManager

open class ChallengePluginPlugin : JavaPlugin() {

    open lateinit var databaseDriver: DatabaseDriver
    open lateinit var challengeManager: ChallengeManager
    open lateinit var languageManager: LanguageManager
    open lateinit var playerDataManager: PlayerDataManager
    open lateinit var settingsInventoryManager: SettingsInventoryManager
    open lateinit var challengeMenuManager: ChallengeMenuManager
    open lateinit var lobbyManager: LobbyManager
    open lateinit var elytraManager: ElytraManager
    open lateinit var blockDropListener: BlockDropListener
    open lateinit var worldPreparationManager: WorldPreparationManager
    open lateinit var experienceBorderListener: ExperienceBorderListener
    open lateinit var bedSpawnManager: BedSpawnManager
    open lateinit var npcManager: NPCManager

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

        // Initialize database first
        databaseDriver = DatabaseDriver(this)
        if (!databaseDriver.initialize()) {
            logger.severe("Failed to initialize database! Plugin will be disabled.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Initialize managers
        languageManager = LanguageManager(this)
        playerDataManager = PlayerDataManager(this)
        bedSpawnManager = BedSpawnManager(this)
        challengeManager = ChallengeManager(this)
        settingsInventoryManager = SettingsInventoryManager(this)
        challengeMenuManager = ChallengeMenuManager(this)
        worldPreparationManager = WorldPreparationManager(this)

        // Initialize lobby manager and elytra manager
        lobbyManager = LobbyManager(this)
        lobbyManager.initialize()
        elytraManager = ElytraManager(this)

        // Initialize NPC manager and load NPCs from database
        npcManager = NPCManager(this)
        npcManager.loadNPCs()

        // Register commands
        // Main challenge commands
        registerCommand("create", CreateCommand(this))
        registerCommand("list", ListCommand(this))
        registerCommand("join", JoinCommand(this))
        registerCommand("leave", LeaveCommand(this))
        registerCommand("info", InfoCommand(this))
        registerCommand("delete", DeleteCommand(this))
        registerCommand("challenge", ChallengeCommand(this))
        // Register prepare command directly since it doesn't extend BaseCommand
        getCommand("prepare")?.setExecutor(PrepareWorldCommand(this))

        // Lobby commands
        registerCommand("lobby", LobbyCommand(this))
        registerCommand("setlobby", SetLobbyCommand(this))

        // Other commands
        registerCommand("lang", LanguageCommand(this))
        registerCommand("pause", PauseCommand(this))
        registerCommand("npc", NPCCommand(this))
        registerCommand("debugdragon", DebugDragonCommand(this))
        registerCommand("debugcolors", DebugColorsCommand(this))
        registerCommand("debugrespawn", DebugRespawnCommand(this))
        registerCommand("debugspawn", DebugSpawnCommand(this))

        // Register listeners
        server.pluginManager.registerEvents(DragonDefeatListener(this), this)
        server.pluginManager.registerEvents(PlayerConnectionListener(this), this)
        server.pluginManager.registerEvents(PlayerHealthListener(this), this)
        server.pluginManager.registerEvents(PortalListener(this), this)
        server.pluginManager.registerEvents(LobbyProtectionListener(this), this)
        server.pluginManager.registerEvents(ServerListPingListener(this), this)
        server.pluginManager.registerEvents(ChatListener(this), this)
        server.pluginManager.registerEvents(BedSpawnListener(this), this)
        server.pluginManager.registerEvents(NPCListener(this), this)
        server.pluginManager.registerEvents(elytraManager, this)
        blockDropListener = BlockDropListener(this)
        server.pluginManager.registerEvents(blockDropListener, this)

        // Create and register the ExperienceBorderListener
        experienceBorderListener = ExperienceBorderListener(this)

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
        settingsInventoryManager.cleanup()
        challengeMenuManager.cleanup()
        experienceBorderListener.cleanup()

        // Unregister event listeners
        if (::elytraManager.isInitialized) {
            org.bukkit.event.HandlerList.unregisterAll(elytraManager)
        }

        // Close database connection
        if (::databaseDriver.isInitialized) {
            databaseDriver.close()
        }

        logger.info(languageManager.getMessage("plugin.disabled"))
    }
}
