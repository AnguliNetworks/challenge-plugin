package li.angu.challengeplugin.managers

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import be.seeseemelk.mockbukkit.entity.PlayerMock
import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.ChallengeStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeManagerTest {
    
    private lateinit var server: ServerMock
    private lateinit var plugin: ChallengePluginPlugin
    private lateinit var challengeManager: ChallengeManager
    private lateinit var player: PlayerMock
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setUp() {
        // Set up the mock server
        server = MockBukkit.mock()
        
        // Mock the plugin - we'll use its default data folder
        plugin = MockBukkit.load(ChallengePluginPlugin::class.java)
        
        // Make sure the data folder exists (we're using the default one)
        plugin.dataFolder.mkdirs()
        
        // Create the challenge manager
        challengeManager = ChallengeManager(plugin)
        
        // Add a mock player
        player = server.addPlayer()
    }
    
    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }
    
    @Test
    fun `test create challenge`() {
        val challenge = challengeManager.createChallenge("Test Challenge")
        
        assertEquals("Test Challenge", challenge.name)
        assertTrue(challenge.worldName.startsWith("challenge_"))
        assertEquals(ChallengeStatus.ACTIVE, challenge.status)
    }
    
    @Test
    fun `test get all challenges`() {
        val challenge1 = challengeManager.createChallenge("Challenge 1")
        val challenge2 = challengeManager.createChallenge("Challenge 2")
        
        val challenges = challengeManager.getAllChallenges()
        
        assertEquals(2, challenges.size)
        assertTrue(challenges.any { it.id == challenge1.id })
        assertTrue(challenges.any { it.id == challenge2.id })
    }
    
    @Test
    fun `test get active challenges`() {
        val challenge1 = challengeManager.createChallenge("Active Challenge")
        val challenge2 = challengeManager.createChallenge("Completed Challenge")
        
        challengeManager.completeChallenge(challenge2.id)
        
        val activeChallenges = challengeManager.getActiveChallenges()
        
        assertEquals(1, activeChallenges.size)
        assertEquals(challenge1.id, activeChallenges[0].id)
    }
    
    @Test
    fun `test join and leave challenge`() {
        val challenge = challengeManager.createChallenge("Test Challenge")
        
        val joinResult = challengeManager.joinChallenge(player, challenge)
        assertTrue(joinResult)
        
        // Verify player is in challenge
        val playerChallenge = challengeManager.getPlayerChallenge(player)
        assertNotNull(playerChallenge)
        assertEquals(challenge.id, playerChallenge.id)
        
        // Leave challenge
        val leaveResult = challengeManager.leaveChallenge(player)
        assertTrue(leaveResult)
        
        // Verify player is no longer in challenge
        val playerChallengeAfterLeave = challengeManager.getPlayerChallenge(player)
        assertNull(playerChallengeAfterLeave)
    }
    
    @Test
    fun `test join already joined challenge`() {
        val challenge = challengeManager.createChallenge("Test Challenge")
        
        // Join first time
        challengeManager.joinChallenge(player, challenge)
        
        // Try to join same challenge again
        val result = challengeManager.joinChallenge(player, challenge)
        
        // Should return false because player is already in this challenge
        assertFalse(result)
    }
    
    @Test
    fun `test join different challenge`() {
        val challenge1 = challengeManager.createChallenge("Challenge 1")
        val challenge2 = challengeManager.createChallenge("Challenge 2")
        
        // Join first challenge
        challengeManager.joinChallenge(player, challenge1)
        
        // Join second challenge
        val result = challengeManager.joinChallenge(player, challenge2)
        
        // Should succeed and automatically leave the first challenge
        assertTrue(result)
        
        // Player should now be in second challenge
        val playerChallenge = challengeManager.getPlayerChallenge(player)
        assertEquals(challenge2.id, playerChallenge?.id)
    }
    
    @Test
    fun `test complete challenge`() {
        val challenge = challengeManager.createChallenge("Test Challenge")
        challengeManager.joinChallenge(player, challenge)
        
        challengeManager.completeChallenge(challenge.id)
        
        val updatedChallenge = challengeManager.getChallenge(challenge.id)
        assertEquals(ChallengeStatus.COMPLETED, updatedChallenge?.status)
        assertNotNull(updatedChallenge?.completedAt)
    }
}