package li.angu.challengeplugin.utils

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LanguageManager(private val plugin: ChallengePluginPlugin) {

    private val languages = ConcurrentHashMap<String, YamlConfiguration>()
    private val playerLanguages = ConcurrentHashMap<UUID, String>()
    private val defaultLanguage = "en"
    private val availableLanguages = listOf("en", "de")

    init {
        loadLanguages()
    }

    private fun loadLanguages() {
        for (lang in availableLanguages) {
            val resource = plugin.getResource("lang/$lang.yml")
            if (resource != null) {
                val config = YamlConfiguration.loadConfiguration(InputStreamReader(resource, StandardCharsets.UTF_8))
                languages[lang] = config
                plugin.logger.info("Loaded language: $lang")
            } else {
                plugin.logger.warning("Could not find language file for: $lang")
            }
        }

        // Create directory if it doesn't exist
        val langDir = File(plugin.dataFolder, "lang")
        if (!langDir.exists()) {
            langDir.mkdirs()
        }
    }

    fun setPlayerLanguage(player: Player, language: String) {
        if (!availableLanguages.contains(language)) {
            player.sendMessage(getMessage("language.not_available", player, "lang" to language))
            return
        }

        playerLanguages[player.uniqueId] = language
        // We don't send a message here because the command does it now
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
