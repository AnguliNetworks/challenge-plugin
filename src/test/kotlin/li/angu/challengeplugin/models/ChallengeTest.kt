package li.angu.challengeplugin.models

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeTest {
    
    private lateinit var challenge: Challenge
    private lateinit var mockPlayer: Player
    private lateinit var mockWorld: World
    private val playerId = UUID.randomUUID()
    
    @BeforeEach
    fun setUp() {
        challenge = Challenge(
            name = "Test Challenge",
            worldName = "test_world"
        )
        
        // Create mock player inventory
        val mockInventory: PlayerInventory = mock()
        
        // Create mock player
        mockPlayer = mock()
        whenever(mockPlayer.uniqueId).thenReturn(playerId)
        whenever(mockPlayer.inventory).thenReturn(mockInventory)
        
        mockWorld = mock()
    }
    
    @Test
    fun `test challenge creation`() {
        assertNotNull(challenge.id)
        assertEquals("Test Challenge", challenge.name)
        assertEquals("test_world", challenge.worldName)
        assertEquals(ChallengeStatus.ACTIVE, challenge.status)
        assertTrue(challenge.players.isEmpty())
        assertNull(challenge.completedAt)
        assertNull(challenge.startedAt)
    }
    
    @Test
    fun `test add player to challenge`() {
        val result = challenge.addPlayer(mockPlayer)
        
        assertTrue(result)
        assertTrue(challenge.players.contains(playerId))
        assertNotNull(challenge.startedAt)
    }
    
    @Test
    fun `test add player to non-active challenge`() {
        challenge.status = ChallengeStatus.COMPLETED
        
        val result = challenge.addPlayer(mockPlayer)
        
        assertFalse(result)
        assertFalse(challenge.players.contains(playerId))
    }
    
    @Test
    fun `test remove player from challenge`() {
        challenge.players.add(playerId)
        
        val result = challenge.removePlayer(mockPlayer)
        
        assertTrue(result)
        assertFalse(challenge.players.contains(playerId))
    }
    
    @Test
    fun `test is player in challenge`() {
        assertFalse(challenge.isPlayerInChallenge(mockPlayer))
        
        challenge.players.add(playerId)
        
        assertTrue(challenge.isPlayerInChallenge(mockPlayer))
    }
    
    @Test
    fun `test complete challenge`() {
        assertNull(challenge.completedAt)
        assertEquals(ChallengeStatus.ACTIVE, challenge.status)
        
        challenge.complete()
        
        assertNotNull(challenge.completedAt)
        assertEquals(ChallengeStatus.COMPLETED, challenge.status)
    }
    
    @Test
    fun `test fail challenge`() {
        assertNull(challenge.completedAt)
        assertEquals(ChallengeStatus.ACTIVE, challenge.status)
        
        challenge.fail()
        
        assertNotNull(challenge.completedAt)
        assertEquals(ChallengeStatus.FAILED, challenge.status)
    }
    
    @Test
    fun `test setup player for challenge`() {
        challenge.setupPlayerForChallenge(mockPlayer, mockWorld)
        
        verify(mockPlayer).gameMode = org.bukkit.GameMode.SURVIVAL
        verify(mockPlayer).health = 20.0
        verify(mockPlayer).foodLevel = 20
        verify(mockPlayer).exp = 0f
        verify(mockPlayer).level = 0
        verify(mockPlayer.inventory).clear()
        verify(mockPlayer).teleport(mockWorld.spawnLocation)
    }
    
    @Test
    fun `test get formatted duration when not started`() {
        val formatted = challenge.getFormattedDuration()
        assertEquals("Not started", formatted)
    }
    
    @Test
    fun `test get formatted duration for active challenge`() {
        challenge.startedAt = Instant.now().minusSeconds(300) // 5 minutes ago
        
        val formatted = challenge.getFormattedDuration()
        assertTrue(formatted.contains("m"))
    }
    
    @Test
    fun `test get formatted duration for completed challenge`() {
        challenge.startedAt = Instant.now().minusSeconds(300) // 5 minutes ago
        challenge.status = ChallengeStatus.COMPLETED
        challenge.completedAt = Instant.now()
        
        val formatted = challenge.getFormattedDuration()
        assertTrue(formatted.contains("m"))
    }
}