package li.angu.challengeplugin

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import li.angu.challengeplugin.commands.ChallengeCommand
import li.angu.challengeplugin.commands.LanguageCommand
import li.angu.challengeplugin.managers.ChallengeManager
import li.angu.challengeplugin.utils.LanguageManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChallengePluginPluginTest {
    
    private lateinit var server: ServerMock
    private lateinit var plugin: ChallengePluginPlugin
    
    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ChallengePluginPlugin::class.java)
    }
    
    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }
    
    @Test
    fun `test plugin initializes properly`() {
        // Verify the plugin is enabled
        assertTrue(plugin.isEnabled)
        
        // Verify managers are initialized
        assertNotNull(plugin.challengeManager)
        assertNotNull(plugin.languageManager)
        
        // Verify commands are registered
        val challengeCommand = server.getCommandMap().getCommand("challenge")
        val langCommand = server.getCommandMap().getCommand("lang")
        
        assertNotNull(challengeCommand)
        assertNotNull(langCommand)
    }
    
    @Test
    fun `test managers are created correctly`() {
        assertTrue(plugin.challengeManager is ChallengeManager)
        assertTrue(plugin.languageManager is LanguageManager)
    }
}