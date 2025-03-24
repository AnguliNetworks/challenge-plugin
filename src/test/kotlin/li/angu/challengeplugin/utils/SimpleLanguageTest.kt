package li.angu.challengeplugin.utils

import org.junit.jupiter.api.Test
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simple independent test for language functionality without using MockBukkit
 */
class SimpleLanguageTest {
    
    private class SimpleLanguageManager {
        private val languages = ConcurrentHashMap<String, YamlConfiguration>()
        private val playerLanguages = ConcurrentHashMap<UUID, String>()
        private val defaultLanguage = "en"
        private val availableLanguages = listOf("en", "de")

        init {
            val enConfig = YamlConfiguration()
            enConfig.set("challenge.created", "§aChallenge '%name%' created successfully!")
            enConfig.set("status.active", "§aActive")

            val deConfig = YamlConfiguration()
            deConfig.set("challenge.created", "§aHerausforderung '%name%' erfolgreich erstellt!")
            deConfig.set("status.active", "§aAktiv")

            languages["en"] = enConfig
            languages["de"] = deConfig
        }

        fun setPlayerLanguage(player: Player, language: String) {
            playerLanguages[player.uniqueId] = language
        }

        fun getPlayerLanguage(player: CommandSender): String {
            if (player !is Player) {
                return defaultLanguage
            }
            return playerLanguages.getOrDefault(player.uniqueId, defaultLanguage)
        }

        fun getMessage(path: String, player: CommandSender? = null, vararg replacements: Pair<String, String>): String {
            val lang = player?.let { getPlayerLanguage(it) } ?: defaultLanguage
            var message = getMessageFromLang(path, lang)

            // Apply replacements
            for ((key, value) in replacements) {
                message = message.replace("%$key%", value)
            }

            return message
        }

        private fun getMessageFromLang(path: String, lang: String): String {
            // Try to get from specified language
            val config = languages[lang] ?: languages[defaultLanguage]
            val message = config?.getString(path)

            // Return message or fallback to path if not found
            return message ?: "§cMissing translation: $path"
        }

        fun getAvailableLanguages(): List<String> {
            return availableLanguages
        }
    }
    
    @Test
    fun `test default language is set to English`() {
        val manager = SimpleLanguageManager()
        val player = mock<Player>()
        val playerId = UUID.randomUUID()
        whenever(player.uniqueId).thenReturn(playerId)
        
        assertEquals("en", manager.getPlayerLanguage(player))
    }
    
    @Test
    fun `test setting and getting player language`() {
        val manager = SimpleLanguageManager()
        val player = mock<Player>()
        val playerId = UUID.randomUUID()
        whenever(player.uniqueId).thenReturn(playerId)
        
        manager.setPlayerLanguage(player, "de")
        assertEquals("de", manager.getPlayerLanguage(player))
    }
    
    @Test
    fun `test getting available languages`() {
        val manager = SimpleLanguageManager()
        val languages = manager.getAvailableLanguages()
        assertTrue(languages.contains("en"))
        assertTrue(languages.contains("de"))
    }
    
    @Test
    fun `test getting message with placeholder replacement`() {
        val manager = SimpleLanguageManager()
        val player = mock<Player>()
        val playerId = UUID.randomUUID()
        whenever(player.uniqueId).thenReturn(playerId)
        
        val message = manager.getMessage("challenge.created", player, "name" to "Test Challenge")
        assertTrue(message.contains("Test Challenge"))
    }
    
    @Test
    fun `test getting message for a specific language`() {
        val manager = SimpleLanguageManager()
        val player = mock<Player>()
        val playerId = UUID.randomUUID()
        whenever(player.uniqueId).thenReturn(playerId)
        
        manager.setPlayerLanguage(player, "de")
        val message = manager.getMessage("status.active", player)
        assertEquals("§aAktiv", message)
    }
}