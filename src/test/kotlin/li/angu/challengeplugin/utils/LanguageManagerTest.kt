package li.angu.challengeplugin.utils

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageManagerTest {
    
    private lateinit var server: ServerMock
    private lateinit var plugin: ChallengePluginPlugin
    private lateinit var languageManager: LanguageManager
    private lateinit var mockPlayer: Player
    
    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ChallengePluginPlugin::class.java)
        languageManager = LanguageManager(plugin)
        
        // Create a mock player
        mockPlayer = mock()
        whenever(mockPlayer.uniqueId).thenReturn(UUID.randomUUID())
    }
    
    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }
    
    @Test
    fun `test default language is set to English`() {
        assertEquals("en", languageManager.getPlayerLanguage(mockPlayer))
    }
    
    @Test
    fun `test setting and getting player language`() {
        languageManager.setPlayerLanguage(mockPlayer, "de")
        assertEquals("de", languageManager.getPlayerLanguage(mockPlayer))
    }
    
    @Test
    fun `test getting available languages`() {
        val languages = languageManager.getAvailableLanguages()
        assertTrue(languages.contains("en"))
        assertTrue(languages.contains("de"))
    }
    
    @Test
    fun `test getting message with placeholder replacement`() {
        val message = languageManager.getMessage("challenge.created", mockPlayer, "name" to "Test Challenge")
        assertTrue(message.contains("Test Challenge"))
    }
    
    @Test
    fun `test getting message for a specific language`() {
        languageManager.setPlayerLanguage(mockPlayer, "de")
        val message = languageManager.getMessage("status.active", mockPlayer)
        assertEquals("§aAktiv", message)
    }
}