package li.angu.challengeplugin.models

import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.time.Instant
import java.time.Duration
import org.bukkit.GameMode
import org.bukkit.GameRule
import li.angu.challengeplugin.utils.TimeFormatter

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    FAILED
}

data class ChallengeSettings(
    var naturalRegeneration: Boolean = true,
    var syncHearts: Boolean = false,
    var blockRandomizer: Boolean = false
)

class Challenge(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val worldName: String,
    val createdAt: Instant = Instant.now(),
    var status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val players: MutableSet<UUID> = mutableSetOf(),
    var completedAt: Instant? = null,
    var startedAt: Instant? = null,
    var pausedAt: Instant? = null,
    var totalPausedDuration: Duration = Duration.ZERO,
    var lastEmptyTimestamp: Instant? = null,
    val settings: ChallengeSettings = ChallengeSettings()
) {
    
    fun addPlayer(player: Player): Boolean {
        if (status != ChallengeStatus.ACTIVE) {
            return false
        }
        
        val added = players.add(player.uniqueId)
        
        // Check if this is the first player (after a pause)
        if (added && players.size == 1) {
            // If the challenge was started but paused, resume the timer
            if (startedAt != null && pausedAt != null) {
                resumeTimer()
            } 
            // If this is the first player ever, set startedAt
            else if (startedAt == null) {
                startedAt = Instant.now()
            }
            
            // Reset the lastEmptyTimestamp since we have players now
            lastEmptyTimestamp = null
        }
        
        return added
    }
    
    fun removePlayer(player: Player): Boolean {
        val removed = players.remove(player.uniqueId)
        
        // If there are no players left in the challenge, pause the timer
        if (removed && players.isEmpty()) {
            pauseTimer()
            // Set the timestamp when the challenge became empty
            lastEmptyTimestamp = Instant.now()
        }
        
        return removed
    }
    
    fun isPlayerInChallenge(player: Player): Boolean {
        return players.contains(player.uniqueId)
    }
    
    fun complete() {
        status = ChallengeStatus.COMPLETED
        completedAt = Instant.now()
    }
    
    fun fail() {
        status = ChallengeStatus.FAILED
        completedAt = Instant.now()
    }
    
    fun pauseTimer() {
        if (pausedAt == null && startedAt != null) {
            pausedAt = Instant.now()
        }
    }
    
    fun resumeTimer() {
        val paused = pausedAt
        if (paused != null) {
            // Calculate the duration the timer was paused
            val pauseDuration = Duration.between(paused, Instant.now())
            // Add to total paused duration
            totalPausedDuration = totalPausedDuration.plus(pauseDuration)
            // Reset the pause timestamp
            pausedAt = null
        }
    }
    
    fun getEffectiveDuration(): Duration {
        if (startedAt == null) {
            return Duration.ZERO
        }
        
        val end = if (status != ChallengeStatus.ACTIVE && completedAt != null) {
            completedAt
        } else if (pausedAt != null) {
            pausedAt
        } else {
            Instant.now()
        }
        
        // Calculate raw duration from start to end
        val rawDuration = Duration.between(startedAt, end)
        
        // Subtract total time paused to get effective duration
        return rawDuration.minus(totalPausedDuration)
    }
    
    fun getFormattedDuration(): String {
        if (startedAt == null) {
            return "Not started"
        }
        
        // Calculate the effective duration (accounting for pauses)
        val effectiveDuration = getEffectiveDuration()
        
        return TimeFormatter.formatDuration(effectiveDuration)
    }
    
    fun isReadyForUnload(): Boolean {
        // If the challenge is empty and has been empty for at least 5 minutes
        return players.isEmpty() && 
               lastEmptyTimestamp != null && 
               Duration.between(lastEmptyTimestamp, Instant.now()).toMinutes() >= 5
    }
    
    fun setupPlayerForChallenge(player: Player, world: World) {
        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.exp = 0f
        player.level = 0
        player.inventory.clear()
        player.teleport(world.spawnLocation)
    }
    
    fun applySettingsToWorld(world: World) {
        world.setGameRule(GameRule.NATURAL_REGENERATION, settings.naturalRegeneration)
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        world.difficulty = org.bukkit.Difficulty.HARD
    }
    
    /**
     * Gets the Nether world associated with this challenge world
     */
    fun getNetherWorld(): World? {
        val netherName = "${worldName}_nether"
        return org.bukkit.Bukkit.getWorld(netherName)
    }
    
    /**
     * Gets the End world associated with this challenge world
     */
    fun getEndWorld(): World? {
        val endName = "${worldName}_the_end"
        return org.bukkit.Bukkit.getWorld(endName)
    }
}