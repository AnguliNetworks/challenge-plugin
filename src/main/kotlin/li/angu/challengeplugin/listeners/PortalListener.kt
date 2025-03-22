package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * Listener to handle player portal events.
 * Ensures players are teleported to the correct nether/end world within a challenge.
 */
class PortalListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player = event.player
        val fromWorld = event.from.world
        
        // Check if player is in a challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player)
        if (challenge == null) {
            // Player is not in a challenge, let vanilla handling work
            return
        }
        
        // Handle portal teleportation based on current world and cause
        when (event.cause) {
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL -> {
                // Player is using a nether portal
                val challengeMainWorld = plugin.server.getWorld(challenge.worldName)
                val challengeNetherWorld = challenge.getNetherWorld()
                
                if (challengeMainWorld != null && challengeNetherWorld != null) {
                    if (fromWorld.environment == World.Environment.NORMAL) {
                        // Going from overworld to nether
                        val ratio = if (fromWorld.name == challenge.worldName) 8.0 else 1.0
                        val destination = calculateDestination(event.from, challengeNetherWorld, ratio)
                        event.to = destination
                        plugin.logger.info("Portal from ${fromWorld.name} to ${challengeNetherWorld.name} for player ${player.name}")
                    } else if (fromWorld.environment == World.Environment.NETHER) {
                        // Going from nether to overworld
                        val ratio = if (fromWorld.name == challenge.getNetherWorld()?.name) 0.125 else 1.0
                        val destination = calculateDestination(event.from, challengeMainWorld, ratio)
                        event.to = destination
                        plugin.logger.info("Portal from ${fromWorld.name} to ${challengeMainWorld.name} for player ${player.name}")
                    }
                }
            }
            
            PlayerTeleportEvent.TeleportCause.END_PORTAL -> {
                // Player is using an end portal
                val challengeMainWorld = plugin.server.getWorld(challenge.worldName)
                val challengeEndWorld = challenge.getEndWorld()
                
                if (challengeMainWorld != null && challengeEndWorld != null) {
                    if (fromWorld.environment == World.Environment.NORMAL) {
                        // Going to the end
                        event.to = challengeEndWorld.spawnLocation
                        plugin.logger.info("End portal to ${challengeEndWorld.name} for player ${player.name}")
                    } else if (fromWorld.environment == World.Environment.THE_END) {
                        // Going back to overworld
                        event.to = challengeMainWorld.spawnLocation
                        plugin.logger.info("End portal to ${challengeMainWorld.name} for player ${player.name}")
                    }
                }
            }
            
            else -> {} // Ignore other teleport causes
        }
    }
    
    /**
     * Calculates the destination location for nether/overworld portals
     */
    private fun calculateDestination(from: Location, toWorld: World, ratio: Double): Location {
        val x = from.x * ratio
        val y = from.y
        val z = from.z * ratio
        
        return Location(toWorld, x, y, z, from.yaw, from.pitch)
    }
}