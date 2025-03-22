package li.angu.challengeplugin.models

import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.time.Instant
import org.bukkit.GameMode
import li.angu.challengeplugin.utils.TimeFormatter

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    FAILED
}

class Challenge(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val worldName: String,
    val createdAt: Instant = Instant.now(),
    var status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val players: MutableSet<UUID> = mutableSetOf(),
    var completedAt: Instant? = null,
    var startedAt: Instant? = null
) {
    
    fun addPlayer(player: Player): Boolean {
        if (status != ChallengeStatus.ACTIVE) {
            return false
        }
        
        val added = players.add(player.uniqueId)
        
        // If this is the first player, set startedAt
        if (added && startedAt == null && players.size == 1) {
            startedAt = Instant.now()
        }
        
        return added
    }
    
    fun removePlayer(player: Player): Boolean {
        return players.remove(player.uniqueId)
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
    
    fun getFormattedDuration(): String {
        if (startedAt == null) {
            return "Not started"
        }
        
        // If challenge is completed or failed, show the fixed duration
        if (status != ChallengeStatus.ACTIVE && completedAt != null) {
            return TimeFormatter.formatDuration(startedAt!!, completedAt)
        }
        
        // Otherwise show the current running duration
        return TimeFormatter.formatDuration(startedAt!!)
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
}