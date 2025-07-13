package li.angu.challengeplugin.models

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Abstract class that represents a setting in the challenge settings menu.
 */
abstract class Setting<T>(
    val id: String,
    val defaultValue: T,
    protected val plugin: ChallengePluginPlugin
) {
    /**
     * Get the display name for this setting.
     */
    fun getName(player: Player): String {
        return plugin.languageManager.getMessage("challenge.settings.$id", player)
    }

    /**
     * Create an item representing this setting with its current value.
     */
    abstract fun createItem(value: T, player: Player): ItemStack

    /**
     * Toggle or cycle the value when clicked.
     */
    abstract fun onClick(currentValue: T): T
}

/**
 * A boolean toggle setting (on/off).
 */
class ToggleSetting(
    id: String,
    defaultValue: Boolean,
    private val enabledMaterial: Material,
    private val disabledMaterial: Material,
    plugin: ChallengePluginPlugin
) : Setting<Boolean>(id, defaultValue, plugin) {

    override fun createItem(value: Boolean, player: Player): ItemStack {
        val material = if (value) enabledMaterial else disabledMaterial
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(getName(player))

        val statusKey = if (value) "enabled" else "disabled"
        meta.lore = listOf(
            plugin.languageManager.getMessage(
                "challenge.settings.status", player, "status" to
                    plugin.languageManager.getMessage("challenge.settings.$statusKey", player)
            ),
            plugin.languageManager.getMessage("challenge.settings.click_to_toggle", player)
        )

        item.itemMeta = meta
        return item
    }

    override fun onClick(currentValue: Boolean): Boolean {
        return !currentValue
    }
}

/**
 * A cyclic setting that rotates through a list of values.
 */
class CycleSetting<T : Enum<T>>(
    id: String,
    defaultValue: T,
    private val values: Array<T>,
    private val materials: Map<T, Material>,
    plugin: ChallengePluginPlugin
) : Setting<T>(id, defaultValue, plugin) {

    override fun createItem(value: T, player: Player): ItemStack {
        val material = materials[value] ?: Material.BARRIER
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(getName(player))

        meta.lore = listOf(
            plugin.languageManager.getMessage(
                "challenge.settings.status", player, "status" to
                    plugin.languageManager.getMessage("challenge.settings.kit.${value.toString().lowercase()}", player)
            ),
            plugin.languageManager.getMessage("challenge.settings.click_to_cycle", player)
        )

        item.itemMeta = meta
        return item
    }

    override fun onClick(currentValue: T): T {
        val currentIndex = values.indexOf(currentValue)
        val nextIndex = (currentIndex + 1) % values.size
        return values[nextIndex]
    }
}