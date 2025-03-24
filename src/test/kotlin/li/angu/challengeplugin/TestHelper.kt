package li.angu.challengeplugin

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import be.seeseemelk.mockbukkit.entity.PlayerMock
import li.angu.challengeplugin.models.Challenge
import li.angu.challengeplugin.models.ChallengeStatus
import java.util.UUID

/**
 * Helper object for test initialization and common test utilities
 */
object TestHelper {
    
    /**
     * Setup the MockBukkit server with the plugin and initialize test environment
     */
    fun setupServer(): Pair<ServerMock, ChallengePluginPlugin> {
        // Clean up any previous server instances
        try {
            MockBukkit.unmock()
        } catch (e: IllegalStateException) {
            // Server wasn't mocked yet, that's fine
        }
        
        // Create a fresh server instance
        val server = MockBukkit.mock()
        
        // Load our plugin
        val plugin = MockBukkit.load(ChallengePluginPlugin::class.java)
        
        // Make sure data folder exists
        plugin.dataFolder.mkdirs()
        
        // Give the server some time to process initialization tasks
        server.scheduler.performTicks(20)
        
        return server to plugin
    }
    
    /**
     * Clean up the MockBukkit server
     */
    fun tearDown() {
        try {
            MockBukkit.unmock()
        } catch (e: IllegalStateException) {
            // Server wasn't mocked, that's fine
        }
    }
    
    /**
     * Create a test player with a specific name
     */
    fun createPlayer(server: ServerMock, name: String): PlayerMock {
        return server.addPlayer(name)
    }
    
    /**
     * Create a mock challenge for testing
     */
    fun createMockChallenge(name: String): Challenge {
        return Challenge(
            name = name,
            worldName = "challenge_${name.lowercase().replace(" ", "_")}"
        )
    }
}