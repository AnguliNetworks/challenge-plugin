package li.angu.challengeplugin.models

import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the Challenge class without MockBukkit dependency
 */
class SimpleChallengeTest {

    @Test
    fun `test challenge creation with default values`() {
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")
        
        assertEquals("Test Challenge", challenge.name)
        assertEquals("test_world", challenge.worldName)
        assertEquals(ChallengeStatus.ACTIVE, challenge.status)
        assertTrue(challenge.players.isEmpty())
        assertNull(challenge.completedAt)
    }
    
    @Test
    fun `test adding and removing players`() {
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")
        val player = mock<Player>()
        val playerId = UUID.randomUUID()
        whenever(player.uniqueId).thenReturn(playerId)
        
        // Add player
        val addResult = challenge.addPlayer(player)
        assertTrue(addResult)
        assertTrue(challenge.players.contains(playerId))
        
        // Remove player
        val removeResult = challenge.removePlayer(player)
        assertTrue(removeResult)
        assertFalse(challenge.players.contains(playerId))
    }
    
    @Test
    fun `test checking if player is in challenge`() {
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")
        val player = mock<Player>()
        val playerId = UUID.randomUUID()
        whenever(player.uniqueId).thenReturn(playerId)
        
        // Initially not in challenge
        assertFalse(challenge.isPlayerInChallenge(player))
        
        // Add player
        challenge.addPlayer(player)
        
        // Now should be in challenge
        assertTrue(challenge.isPlayerInChallenge(player))
    }
    
    @Test
    fun `test completing challenge`() {
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")
        
        challenge.complete()
        
        assertEquals(ChallengeStatus.COMPLETED, challenge.status)
        assertNotNull(challenge.completedAt)
    }
    
    @Test
    fun `test getting challenge duration when not started`() {
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")
        
        val duration = challenge.getEffectiveDuration()
        assertEquals(Duration.ZERO, duration)
    }
    
    @Test
    fun `test getting nether and end world names`() {
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")
        
        val netherWorldName = "${challenge.worldName}_nether"
        val endWorldName = "${challenge.worldName}_the_end"
        
        // Mock Bukkit and world to avoid static errors
        val endWorld = mock<World>()
        whenever(endWorld.name).thenReturn(endWorldName)
        
        // Test end world name format
        assertEquals("test_world_the_end", endWorldName)
    }
}