package li.angu.challengeplugin

import li.angu.challengeplugin.models.Challenge
import java.util.UUID

/**
 * Helper object for common test utilities
 */
object TestHelper {
    
    /**
     * Create a mock challenge for testing
     */
    fun createMockChallenge(name: String): Challenge {
        return Challenge(
            name = name,
            worldName = "challenge_${name.lowercase().replace(" ", "_")}",
            creatorUuid = UUID.randomUUID()
        )
    }
}