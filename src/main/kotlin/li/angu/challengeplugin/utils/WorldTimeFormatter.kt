package li.angu.challengeplugin.utils

import org.bukkit.World

/**
 * Utility class for formatting Minecraft world time into human-readable format.
 * Minecraft time: 0-24000 ticks per day
 * - 0 ticks = 6:00 AM (sunrise)
 * - 6000 ticks = 12:00 PM (noon)
 * - 12000 ticks = 6:00 PM (sunset)
 * - 18000 ticks = 12:00 AM (midnight)
 */
object WorldTimeFormatter {

    /**
     * Formats the world's time into a colored string representing the time of day.
     * Returns format like "§68 am" with appropriate color codes.
     *
     * Colors:
     * - Morning (6 AM - 12 PM): §6 (Gold)
     * - Day (12 PM - 6 PM): §e (Yellow)
     * - Evening (6 PM - 12 AM): §c (Red/Orange)
     * - Night (12 AM - 6 AM): §7 (Gray)
     */
    fun getFormattedTime(world: World): String {
        val ticks = world.time
        val normalizedTicks = ticks % 24000 // Ensure we're in 0-24000 range

        // Convert ticks to hours (ticks offset by 6000 since 0 ticks = 6 AM)
        val totalMinutes = ((normalizedTicks + 6000) % 24000) * 24 * 60 / 24000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        // Determine 12-hour format
        val displayHour = when {
            hours == 0L -> 12
            hours > 12 -> hours - 12
            else -> hours.toInt()
        }
        val amPm = if (hours < 12) "am" else "pm"

        // Determine color based on time of day
        val color = getTimeColor(normalizedTicks)

        // Format: "8 am" or "3 pm"
        return "$color$displayHour $amPm"
    }

    /**
     * Gets the formatted dimension name with appropriate color.
     * - Nether: §c (Red)
     * - End: §d (Light Purple)
     */
    fun getFormattedDimension(environment: World.Environment): String {
        return when (environment) {
            World.Environment.NETHER -> "§cNether"
            World.Environment.THE_END -> "§dEnd"
            else -> "" // Should not happen, but return empty string for safety
        }
    }

    /**
     * Gets the color code based on the time of day.
     */
    private fun getTimeColor(ticks: Long): String {
        return when (ticks) {
            in 0..5999 -> "§6"       // Morning (6 AM - 12 PM): Gold
            in 6000..11999 -> "§e"   // Day (12 PM - 6 PM): Yellow
            in 12000..17999 -> "§c"  // Evening (6 PM - 12 AM): Red/Orange
            else -> "§7"              // Night (12 AM - 6 AM): Gray
        }
    }

    /**
     * Gets the world info string to append to the timer display.
     * Returns either formatted time for overworld or dimension name for nether/end.
     */
    fun getWorldInfo(world: World): String {
        return when (world.environment) {
            World.Environment.NORMAL -> getFormattedTime(world)
            World.Environment.NETHER -> getFormattedDimension(World.Environment.NETHER)
            World.Environment.THE_END -> getFormattedDimension(World.Environment.THE_END)
            else -> getFormattedTime(world) // Fallback for CUSTOM or other environments
        }
    }
}
