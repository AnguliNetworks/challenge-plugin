package li.angu.challengeplugin.listeners

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import be.seeseemelk.mockbukkit.entity.PlayerMock
import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.managers.ChallengeManager
import li.angu.challengeplugin.models.Challenge
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EnderDragon
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class DragonDefeatListenerTest {

    private lateinit var server: ServerMock
    private lateinit var plugin: ChallengePluginPlugin
    private lateinit var listener: DragonDefeatListener
    private lateinit var challengeManager: ChallengeManager
    private lateinit var player: PlayerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ChallengePluginPlugin::class.java)

        // Mock challenge manager
        challengeManager = mock()

        // Replace plugin's challenge manager with our mock
        plugin.challengeManager = challengeManager

        // Create the listener
        listener = DragonDefeatListener(plugin)

        // Add a mock player
        player = server.addPlayer()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `test ender dragon death completes challenge`() {
        // Create a mock challenge
        val challenge = Challenge(name = "Test Challenge", worldName = "dragon_world")

        // Mock the dragon and world
        val enderDragon: EnderDragon = mock()
        val world: World = mock()
        whenever(enderDragon.world).thenReturn(world)
        whenever(world.name).thenReturn("dragon_world")

        // Setup the challenge manager mock
        whenever(challengeManager.getAllChallenges()).thenReturn(listOf(challenge))
        whenever(challengeManager.getChallenge(challenge.id)).thenReturn(challenge)

        // Create death event
        val event = EntityDeathEvent(enderDragon, listOf())

        // Call the listener
        listener.onEnderDragonDeath(event)

        // Verify challenge was completed
        verify(challengeManager).completeChallenge(challenge.id)
    }

    @Test
    fun `test player death in challenge saves location`() {
        // Create a mock challenge
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")

        // Setup player location
        val location = Location(player.world, 100.0, 64.0, 100.0)
        player.teleport(location)

        // Setup the challenge manager mock
        whenever(challengeManager.getPlayerChallenge(player)).thenReturn(challenge)

        // Create death event
        val event = PlayerDeathEvent(player, listOf(), 0, "Player died")

        // Call the listener
        listener.onPlayerDeath(event)

        // Verify message was sent
        player.assertSaid("§cYou died! In hardcore mode, you are now a spectator.")
    }

    @Test
    fun `test player respawn after death sets spectator mode at death location`() {
        // Create a mock challenge
        val challenge = Challenge(name = "Test Challenge", worldName = "test_world")

        // Setup player location
        val location = Location(player.world, 100.0, 64.0, 100.0)
        player.teleport(location)

        // Setup the challenge manager mock
        whenever(challengeManager.getPlayerChallenge(player)).thenReturn(challenge)

        // First trigger death to store location
        val deathEvent = PlayerDeathEvent(player, listOf(), 0, "Player died")
        listener.onPlayerDeath(deathEvent)

        // Then trigger respawn
        val respawnEvent = PlayerRespawnEvent(player, location, false)
        listener.onPlayerRespawn(respawnEvent)

        // Verify player is now in spectator mode
        assertEquals(GameMode.SPECTATOR, player.gameMode)

        // Verify respawn location was set to the death location
        assertEquals(location, respawnEvent.respawnLocation)
    }
}
