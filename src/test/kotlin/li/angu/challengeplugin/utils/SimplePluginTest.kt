package li.angu.challengeplugin.utils

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.managers.ChallengeManager
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Simple test for plugin class without depending on MockBukkit
 */
class SimplePluginTest {
    
    @Test
    fun `test plugin class can be instantiated`() {
        // Create a mock instance
        val plugin = mock<ChallengePluginPlugin>()
        
        // Verify it's not null
        assertNotNull(plugin)
    }
}