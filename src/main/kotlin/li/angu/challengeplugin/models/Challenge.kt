package li.angu.challengeplugin.models

import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.time.Instant
import java.time.Duration
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import li.angu.challengeplugin.utils.TimeFormatter

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    FAILED
}

enum class StarterKit {
    NONE,
    BASIC,    // Crafting table, logs, stone tools
    STONE,    // Full stone gear, leather armor, food, crafting table, logs
    IRON,     // Full iron gear and armor, basic supplies
    DIAMOND   // Full diamond gear and armor, bow, golden apples, basic supplies
}

data class ChallengeSettings(
    var naturalRegeneration: Boolean = true,
    var syncHearts: Boolean = false,
    var blockRandomizer: Boolean = false,
    var starterKit: StarterKit = StarterKit.NONE,
    var levelWorldBorder: Boolean = false,
    var borderSize: Double = 3.0 // Initial border size of 3 blocks
)

class Challenge(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    var worldName: String,
    val creatorUuid: UUID,
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
        player.saturation = 6f
        player.exp = 0f
        player.level = 0
        player.inventory.clear()
        
        // Give starter kit if enabled
        when (settings.starterKit) {
            StarterKit.BASIC -> giveBasicKit(player)
            StarterKit.STONE -> giveStoneKit(player)
            StarterKit.IRON -> giveIronKit(player)
            StarterKit.DIAMOND -> giveDiamondKit(player)
            StarterKit.NONE -> {} // No kit
        }
        
        player.teleport(world.spawnLocation)
    }
    
    private fun giveBasicKit(player: Player) {
        player.inventory.addItem(
            ItemStack(Material.CRAFTING_TABLE, 1),
            ItemStack(Material.OAK_LOG, 4),
            ItemStack(Material.STONE_AXE, 1),
            ItemStack(Material.STONE_PICKAXE, 1)
        )
    }
    
    private fun giveStoneKit(player: Player) {
        // Stone tools
        player.inventory.addItem(
            ItemStack(Material.STONE_SWORD, 1),
            ItemStack(Material.STONE_PICKAXE, 1),
            ItemStack(Material.STONE_AXE, 1),
            ItemStack(Material.STONE_SHOVEL, 1)
        )
        
        // Leather armor
        player.inventory.addItem(
            ItemStack(Material.LEATHER_HELMET, 1),
            ItemStack(Material.LEATHER_CHESTPLATE, 1),
            ItemStack(Material.LEATHER_LEGGINGS, 1),
            ItemStack(Material.LEATHER_BOOTS, 1)
        )
        
        // Basic supplies
        player.inventory.addItem(
            ItemStack(Material.CRAFTING_TABLE, 1),
            ItemStack(Material.OAK_LOG, 8),
            ItemStack(Material.COOKED_BEEF, 8)
        )
    }
    
    private fun giveIronKit(player: Player) {
        // Iron tools
        player.inventory.addItem(
            ItemStack(Material.IRON_SWORD, 1),
            ItemStack(Material.IRON_PICKAXE, 1),
            ItemStack(Material.IRON_AXE, 1),
            ItemStack(Material.IRON_SHOVEL, 1)
        )
        
        // Iron armor
        player.inventory.addItem(
            ItemStack(Material.IRON_HELMET, 1),
            ItemStack(Material.IRON_CHESTPLATE, 1),
            ItemStack(Material.IRON_LEGGINGS, 1),
            ItemStack(Material.IRON_BOOTS, 1)
        )
        
        // Basic supplies
        player.inventory.addItem(
            ItemStack(Material.CRAFTING_TABLE, 1),
            ItemStack(Material.FURNACE, 1),
            ItemStack(Material.OAK_LOG, 16),
            ItemStack(Material.COOKED_BEEF, 16),
            ItemStack(Material.TORCH, 16)
        )
    }
    
    private fun giveDiamondKit(player: Player) {
        // Diamond tools
        player.inventory.addItem(
            ItemStack(Material.DIAMOND_SWORD, 1),
            ItemStack(Material.DIAMOND_PICKAXE, 1),
            ItemStack(Material.DIAMOND_AXE, 1),
            ItemStack(Material.DIAMOND_SHOVEL, 1)
        )
        
        // Diamond armor
        player.inventory.addItem(
            ItemStack(Material.DIAMOND_HELMET, 1),
            ItemStack(Material.DIAMOND_CHESTPLATE, 1),
            ItemStack(Material.DIAMOND_LEGGINGS, 1),
            ItemStack(Material.DIAMOND_BOOTS, 1)
        )
        
        // Bow and arrows
        player.inventory.addItem(
            ItemStack(Material.BOW, 1),
            ItemStack(Material.ARROW, 64)
        )
        
        // Golden apples and other supplies
        player.inventory.addItem(
            ItemStack(Material.GOLDEN_APPLE, 3),
            ItemStack(Material.CRAFTING_TABLE, 1),
            ItemStack(Material.FURNACE, 1),
            ItemStack(Material.OAK_LOG, 32),
            ItemStack(Material.COOKED_BEEF, 32),
            ItemStack(Material.TORCH, 32)
        )
    }
    
    fun applySettingsToWorld(world: World) {
        world.setGameRule(GameRule.NATURAL_REGENERATION, settings.naturalRegeneration)
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        world.difficulty = org.bukkit.Difficulty.HARD
        
        // For levelWorldBorder:
        // We don't modify the world's border directly anymore
        // Instead, individual player borders are managed by the ExperienceBorderListener
        // This allows mobs to spawn outside the visible border, fixing the issue
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